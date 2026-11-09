package com.example.personalassistant.feature.timetable.data.repository

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28 P3/P4：课程 + 课时这条路径的用例
 *
 * 覆盖三件最容易出错的事：
 * 1. **空课时兜底** —— 课程在课时表里没有记录时，用主时段镜像兜底出单课时
 *    （否则一门课会从课表上凭空消失）
 * 2. **整门课保存** —— 课程 + 课时一起写，三列镜像同步成第一个课时
 * 3. **冲突检测** —— semester == null 时要跨全部学期查（不能静默失效）
 */
class TimetableRepositorySessionsTest {

    private fun course(
        id: Long = 0L,
        name: String = "高数",
        dayOfWeek: Int = 1,
        startTime: Int = 480,
        endTime: Int = 540,
        semester: String? = "2024-2025-1",
        sessions: List<CourseSession> = emptyList(),
    ) = Course(
        id = id,
        name = name,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        color = 0xFFE57373.toInt(),
        semester = semester,
        sessions = sessions,
    )

    private fun session(
        dayOfWeek: Int = 1,
        startTime: Int = 480,
        endTime: Int = 540,
        sortOrder: Int = 0,
    ) = CourseSession(
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        sortOrder = sortOrder,
    )

    // ===== 空课时兜底 =====

    @Test
    fun `observeCoursesWithSessions 课程没有课时记录时兜底出单课时`() = runBlocking {
        val repo = FakeTimetableRepository()
        repo.seedCourse(course(id = 1L, dayOfWeek = 3, startTime = 480, endTime = 540))

        val courses = repo.observeCoursesWithSessions("2024-2025-1").first()

        assertEquals(1, courses.size)
        assertEquals(1, courses[0].sessions.size)
        val s = courses[0].sessions[0]
        assertEquals(3, s.dayOfWeek)
        assertEquals(480, s.startTime)
        assertEquals(540, s.endTime)
        assertEquals(1L, s.courseId)
    }

    @Test
    fun `getCourseWithSessions 课程没有课时记录时兜底出单课时`() = runBlocking {
        val repo = FakeTimetableRepository()
        repo.seedCourse(course(id = 1L, dayOfWeek = 3, startTime = 480, endTime = 540))

        val c = repo.getCourseWithSessions(1L)

        assertNotNull(c)
        assertEquals(1, c!!.sessions.size)
        assertEquals(3, c.sessions[0].dayOfWeek)
    }

    @Test
    fun `getCourseWithSessions 课程不存在返回 null`() = runBlocking {
        val repo = FakeTimetableRepository()
        assertNull(repo.getCourseWithSessions(999L))
    }

    @Test
    fun `observeCoursesWithSessions 有课时记录时返回真实课时`() = runBlocking {
        val repo = FakeTimetableRepository()
        repo.seedCourse(course(id = 1L, dayOfWeek = 1, startTime = 480, endTime = 540))
        repo.seedSessions(
            1L,
            listOf(
                session(dayOfWeek = 1, startTime = 480, endTime = 540, sortOrder = 0),
                session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 1),
            ),
        )

        val courses = repo.observeCoursesWithSessions("2024-2025-1").first()

        assertEquals(2, courses[0].sessions.size)
        assertEquals(1, courses[0].sessions[0].dayOfWeek)
        assertEquals(3, courses[0].sessions[1].dayOfWeek)
    }

    @Test
    fun `observeCoursesWithSessions semester 为 null 时返回全部学期`() = runBlocking {
        val repo = FakeTimetableRepository()
        repo.seedCourse(course(id = 1L, semester = "2024-2025-1"))
        repo.seedCourse(course(id = 2L, semester = "2024-2025-2"))

        val all = repo.observeCoursesWithSessions(null).first()
        val one = repo.observeCoursesWithSessions("2024-2025-1").first()

        assertEquals(2, all.size)
        assertEquals(1, one.size)
    }

    // ===== 整门课保存 =====

    @Test
    fun `upsertCourseWithSessions 写入全部课时并同步三列镜像`() = runBlocking {
        val repo = FakeTimetableRepository()

        val id = repo.upsertCourseWithSessions(
            course(
                id = 0L,
                // 故意让课程自身的三列与第一个课时不一致 —— 保存后应被第一个课时覆盖
                dayOfWeek = 6,
                startTime = 100,
                endTime = 200,
                sessions = listOf(
                    session(dayOfWeek = 1, startTime = 480, endTime = 540, sortOrder = 0),
                    session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 1),
                ),
            ),
        )

        val saved = repo.getCourseWithSessions(id)
        assertNotNull(saved)
        assertEquals(2, saved!!.sessions.size)
        // 三列镜像 = 第一个课时
        assertEquals(1, saved.dayOfWeek)
        assertEquals(480, saved.startTime)
        assertEquals(540, saved.endTime)
    }

    @Test
    fun `upsertCourseWithSessions 重新编号 sortOrder 保证唯一索引不炸`() = runBlocking {
        val repo = FakeTimetableRepository()

        val id = repo.upsertCourseWithSessions(
            course(
                id = 0L,
                sessions = listOf(
                    // 故意给重复的 sortOrder
                    session(dayOfWeek = 1, startTime = 480, endTime = 540, sortOrder = 0),
                    session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 0),
                ),
            ),
        )

        val sessions = repo.sessionsOf(id)
        assertEquals(2, sessions.size)
        assertEquals(listOf(0, 1), sessions.map { it.sortOrder })
    }

    @Test
    fun `upsertCourseWithSessions 再次保存会替换掉旧课时`() = runBlocking {
        val repo = FakeTimetableRepository()

        val id = repo.upsertCourseWithSessions(
            course(
                id = 0L,
                sessions = listOf(
                    session(dayOfWeek = 1, startTime = 480, endTime = 540, sortOrder = 0),
                    session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 1),
                ),
            ),
        )
        assertEquals(2, repo.sessionsOf(id).size)

        // 再保存一次，只剩一个课时
        repo.upsertCourseWithSessions(
            course(
                id = id,
                sessions = listOf(session(dayOfWeek = 5, startTime = 600, endTime = 660, sortOrder = 0)),
            ),
        )
        assertEquals(1, repo.sessionsOf(id).size)
    }

    @Test
    fun `upsertCourseWithSessions 没有课时时保留课程自身的三列`() = runBlocking {
        val repo = FakeTimetableRepository()

        val id = repo.upsertCourseWithSessions(
            course(id = 0L, dayOfWeek = 2, startTime = 480, endTime = 540),
        )

        val saved = repo.getCourseWithSessions(id)
        assertNotNull(saved)
        assertEquals(2, saved!!.dayOfWeek)
        assertEquals(480, saved.startTime)
        assertEquals(540, saved.endTime)
        // 兜底出的单课时与三列一致
        assertEquals(1, saved.sessions.size)
        assertEquals(2, saved.sessions[0].dayOfWeek)
    }

    // ===== 冲突检测 =====

    @Test
    fun `findSessionConflicts 命中别门的课时`() = runBlocking {
        val repo = FakeTimetableRepository()
        repo.seedCourse(course(id = 10L, dayOfWeek = 3, startTime = 540, endTime = 600))

        val hits = repo.findSessionConflicts(
            semester = "2024-2025-1",
            session = session(dayOfWeek = 3, startTime = 555, endTime = 615),
            excludeCourseId = 0L,
        )

        assertEquals(1, hits.size)
        assertEquals(10L, hits[0].id)
    }

    @Test
    fun `findSessionConflicts 排除自身课程`() = runBlocking {
        val repo = FakeTimetableRepository()
        repo.seedCourse(course(id = 10L, dayOfWeek = 3, startTime = 540, endTime = 600))

        val hits = repo.findSessionConflicts(
            semester = "2024-2025-1",
            session = session(dayOfWeek = 3, startTime = 555, endTime = 615),
            excludeCourseId = 10L,
        )

        assertTrue(hits.isEmpty())
    }

    @Test
    fun `findSessionConflicts semester 为 null 时跨全部学期查`() = runBlocking {
        val repo = FakeTimetableRepository()
        // 已有课在**另一个**学期
        repo.seedCourse(
            course(id = 10L, semester = "2024-2025-2", dayOfWeek = 3, startTime = 540, endTime = 600),
        )

        val hits = repo.findSessionConflicts(
            semester = null,
            session = session(dayOfWeek = 3, startTime = 555, endTime = 615),
            excludeCourseId = 0L,
        )

        // ⚠️ 这一条最关键：semester == null 若写成 `WHERE semester = :semester`，
        // 匹配不到任何行，冲突检测会**静默失效**。
        assertEquals(1, hits.size)
        assertEquals(10L, hits[0].id)
    }

    @Test
    fun `findSessionConflicts 不同学期不互相冲突`() = runBlocking {
        val repo = FakeTimetableRepository()
        repo.seedCourse(
            course(id = 10L, semester = "2024-2025-2", dayOfWeek = 3, startTime = 540, endTime = 600),
        )

        val hits = repo.findSessionConflicts(
            semester = "2024-2025-1",
            session = session(dayOfWeek = 3, startTime = 555, endTime = 615),
            excludeCourseId = 0L,
        )

        assertTrue(hits.isEmpty())
    }

    @Test
    fun `findSessionConflicts 用对方的课时判断而不是主时段镜像`() = runBlocking {
        val repo = FakeTimetableRepository()
        // 对方的主时段是周一 8-9，但它的**第二个**课时在周三 9-10
        repo.seedCourse(course(id = 10L, dayOfWeek = 1, startTime = 480, endTime = 540))
        repo.seedSessions(
            10L,
            listOf(
                session(dayOfWeek = 1, startTime = 480, endTime = 540, sortOrder = 0),
                session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 1),
            ),
        )

        val hits = repo.findSessionConflicts(
            semester = "2024-2025-1",
            session = session(dayOfWeek = 3, startTime = 555, endTime = 615),
            excludeCourseId = 0L,
        )

        assertEquals(1, hits.size)
    }

    @Test
    fun `findSessionConflicts 对方没有课时记录时用主时段镜像兜底判断`() = runBlocking {
        val repo = FakeTimetableRepository()
        repo.seedCourse(course(id = 10L, dayOfWeek = 3, startTime = 540, endTime = 600))

        val hits = repo.findSessionConflicts(
            semester = "2024-2025-1",
            session = session(dayOfWeek = 3, startTime = 555, endTime = 615),
            excludeCourseId = 0L,
        )

        assertEquals(1, hits.size)
        // 返回的课程也带上了兜底出的单课时
        assertFalse(hits[0].sessions.isEmpty())
    }
}
