package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * UpsertCourseUseCase 冲突检测算法测试
 *
 * 使用手写 fake repository（不引入 mockk），用 runBlocking 跑 suspend 代码
 *
 * ## Phase 28：改成逐课时检测
 *
 * - 冲突检测按 **每个课时** 逐个查（不是只看课程身上的那一个时段）
 * - **同门课内部课时重叠**要单独查（Repository 排除了被编辑课程自己，查不出来）
 */
class UpsertCourseUseCaseTest {

    private fun course(
        id: Long = 0L,
        name: String = "高数",
        teacher: String = "",
        dayOfWeek: Int = 1,
        startTime: Int = 540,
        endTime: Int = 600,
        semester: String? = "2024-2025-1",
        sessions: List<CourseSession> = emptyList(),
    ) = Course(
        id = id,
        name = name,
        teacher = teacher,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        color = 0xFFE57373.toInt(),
        semester = semester,
        sessions = sessions,
    )

    private fun session(
        courseId: Long = 0L,
        dayOfWeek: Int = 1,
        startTime: Int = 540,
        endTime: Int = 600,
        sortOrder: Int = 0,
    ) = CourseSession(
        courseId = courseId,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        sortOrder = sortOrder,
    )

    /**
     * 行为可控的最小 fake repo：记录写入的课程、按课时做冲突检测
     */
    private class FakeTimetableRepository(
        private val existing: List<Course>,
    ) : TimetableRepository {

        val upserted = mutableListOf<Course>()
        var deletedIds = mutableListOf<Long>()

        override fun observeCoursesBySemester(semester: String): Flow<List<Course>> = flowOf(existing)
        override fun observeAllCourses(): Flow<List<Course>> = flowOf(existing)
        override fun observeCoursesByDay(semester: String, dayOfWeek: Int): Flow<List<Course>> = flowOf(emptyList())
        override suspend fun getCourseById(id: Long): Course? = existing.firstOrNull { it.id == id }
        override suspend fun findConflicts(
            semester: String?,
            dayOfWeek: Int,
            startTime: Int,
            endTime: Int,
            excludeId: Long,
        ): List<Course> = existing.filter { c ->
            c.id != excludeId &&
                (semester == null || c.semester == semester) &&
                c.dayOfWeek == dayOfWeek &&
                c.startTime < endTime &&
                c.endTime > startTime
        }

        override suspend fun upsertCourse(course: Course): Long {
            upserted += course
            return course.id
        }

        override suspend fun upsertCourseWithSessions(course: Course): Long {
            upserted += course
            return course.id
        }

        override fun observeCoursesWithSessions(semester: String?): Flow<List<Course>> = flowOf(existing)
        override suspend fun getCourseWithSessions(courseId: Long): Course? =
            existing.firstOrNull { it.id == courseId }

        override suspend fun findSessionConflicts(
            semester: String?,
            session: CourseSession,
            excludeCourseId: Long,
        ): List<Course> = existing.filter { c ->
            c.id != excludeCourseId &&
                (semester == null || c.semester == semester) &&
                c.effectiveSessions.any { it.overlaps(session) }
        }

        override suspend fun deleteCourse(id: Long) { deletedIds += id }
        override suspend fun switchSemester(name: String) {}
        override suspend fun renameSemesterReferences(oldName: String, newName: String) {}
        override fun observeAllSemesters(): Flow<List<Semester>> = flowOf(emptyList())
        override fun observeCurrentSemester(): Flow<Semester?> = flowOf(null)
        override suspend fun getSemesterByName(name: String): Semester? = null
        override suspend fun upsertSemester(semester: Semester) {}
        override suspend fun deleteSemester(name: String) {}

        // Phase 8 Sub-step 3 全局搜索（占位实现 — 此 UseCase 测试不关心搜索）
        override fun searchCourses(keyword: String): Flow<List<Course>> = flowOf(emptyList())
    }

    @Test
    fun `force=false with no conflict writes the course`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 2, startTime = 600, endTime = 660))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(id = 0L, dayOfWeek = 1, startTime = 540, endTime = 600)
        val result = useCase(newCourse)

        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, repo.upserted.size)
        val stored = repo.upserted[0]
        assertEquals(newCourse.name, stored.name)
        assertEquals(newCourse.dayOfWeek, stored.dayOfWeek)
        assertEquals(newCourse.startTime, stored.startTime)
        assertEquals(newCourse.endTime, stored.endTime)
        assertEquals(newCourse.semester, stored.semester)
    }

    @Test
    fun `force=false with conflict throws CourseConflictException`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(id = 0L, dayOfWeek = 1, startTime = 555, endTime = 615)
        try {
            useCase(newCourse)
            fail("expected CourseConflictException")
        } catch (e: CourseConflictException) {
            assertNotNull(e.conflict)
            assertTrue(e.conflict.hasConflict)
            assertEquals(1, e.conflict.conflicts.size)
        }
        assertEquals(0, repo.upserted.size)
    }

    @Test
    fun `force=true with conflict writes the course`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(id = 0L, dayOfWeek = 1, startTime = 555, endTime = 615)
        val result = useCase(newCourse, force = true)

        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, repo.upserted.size)
    }

    @Test
    fun `editing own course does not flag self as conflict`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val edited = course(id = 10L, dayOfWeek = 1, startTime = 555, endTime = 615)
        val result = useCase(edited)

        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, repo.upserted.size)
    }

    @Test
    fun `different day does not trigger conflict`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(id = 0L, dayOfWeek = 2, startTime = 540, endTime = 600)
        val result = useCase(newCourse)

        assertTrue(result.conflicts.isEmpty())
    }

    /**
     * Phase 12：teacher 字段透传（推翻之前"UI 写死空串"的设计）
     */
    @Test
    fun `teacher 字段被原样保存到 DB`() = runBlocking {
        val repo = FakeTimetableRepository(emptyList())
        val useCase = UpsertCourseUseCase(repo)

        val c = course(id = 0L, name = "高数", teacher = "张老师", dayOfWeek = 1)
        useCase(c)

        assertEquals(1, repo.upserted.size)
        val stored = repo.upserted[0]
        assertEquals("张老师", stored.teacher)
    }

    @Test
    fun `teacher 字段为空串也能保存（默认情况）`() = runBlocking {
        val repo = FakeTimetableRepository(emptyList())
        val useCase = UpsertCourseUseCase(repo)

        val c = course(id = 0L, name = "高数", teacher = "", dayOfWeek = 1)
        useCase(c)

        assertEquals(1, repo.upserted.size)
        assertEquals("", repo.upserted[0].teacher)
    }

    // ===== Phase 28：多课时 =====

    @Test
    fun `第二个课时与别的课冲突也算冲突`() = runBlocking {
        // 已有课在周三 9:00-10:00
        val existing = listOf(course(id = 10L, dayOfWeek = 3, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        // 新课有两个课时：周一 8:00-9:00（不冲突）+ 周三 9:00-10:00（撞上了）
        val newCourse = course(
            id = 0L,
            dayOfWeek = 1,
            startTime = 480,
            endTime = 540,
            sessions = listOf(
                session(dayOfWeek = 1, startTime = 480, endTime = 540, sortOrder = 0),
                session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 1),
            ),
        )
        try {
            useCase(newCourse)
            fail("expected CourseConflictException")
        } catch (e: CourseConflictException) {
            assertEquals(1, e.conflict.conflicts.size)
            // 明细里能看出「是周三那个课时撞的」
            assertEquals(1, e.conflict.sessionDetails.size)
            assertEquals(3, e.conflict.sessionDetails[0].session.dayOfWeek)
            assertEquals(10L, e.conflict.sessionDetails[0].conflictingCourse.id)
        }
        assertEquals(0, repo.upserted.size)
    }

    @Test
    fun `所有课时都不冲突时正常写入`() = runBlocking {
        val existing = listOf(course(id = 10L, dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(
            id = 0L,
            dayOfWeek = 2,
            startTime = 480,
            endTime = 540,
            sessions = listOf(
                session(dayOfWeek = 2, startTime = 480, endTime = 540, sortOrder = 0),
                session(dayOfWeek = 4, startTime = 540, endTime = 600, sortOrder = 1),
            ),
        )
        val result = useCase(newCourse)

        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, repo.upserted.size)
        assertEquals(2, repo.upserted[0].sessions.size)
    }

    @Test
    fun `同一门课内部课时重叠被检出`() = runBlocking {
        val repo = FakeTimetableRepository(emptyList())
        val useCase = UpsertCourseUseCase(repo)

        // 两个课时自己撞：周一 9:00-10:00 与 周一 9:30-11:00
        val newCourse = course(
            id = 0L,
            dayOfWeek = 1,
            startTime = 540,
            endTime = 600,
            sessions = listOf(
                session(dayOfWeek = 1, startTime = 540, endTime = 600, sortOrder = 0),
                session(dayOfWeek = 1, startTime = 570, endTime = 660, sortOrder = 1),
            ),
        )
        try {
            useCase(newCourse)
            fail("expected CourseConflictException")
        } catch (e: CourseConflictException) {
            assertTrue(e.conflict.hasInternalOverlap)
            assertTrue(e.conflict.hasAnyConflict)
            assertEquals(1, e.conflict.internalOverlaps.size)
            // 没有跨课程冲突
            assertTrue(e.conflict.conflicts.isEmpty())
        }
        assertEquals(0, repo.upserted.size)
    }

    @Test
    fun `force=true 时同门课内部重叠也放行`() = runBlocking {
        val repo = FakeTimetableRepository(emptyList())
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(
            id = 0L,
            dayOfWeek = 1,
            startTime = 540,
            endTime = 600,
            sessions = listOf(
                session(dayOfWeek = 1, startTime = 540, endTime = 600, sortOrder = 0),
                session(dayOfWeek = 1, startTime = 570, endTime = 660, sortOrder = 1),
            ),
        )
        val result = useCase(newCourse, force = true)

        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, repo.upserted.size)
    }

    @Test
    fun `同门课内部课时首尾相接不算重叠`() = runBlocking {
        val repo = FakeTimetableRepository(emptyList())
        val useCase = UpsertCourseUseCase(repo)

        // 周一 9:00-10:00 与 周一 10:00-11:00 —— 连排，不算冲突
        val newCourse = course(
            id = 0L,
            dayOfWeek = 1,
            startTime = 540,
            endTime = 600,
            sessions = listOf(
                session(dayOfWeek = 1, startTime = 540, endTime = 600, sortOrder = 0),
                session(dayOfWeek = 1, startTime = 600, endTime = 660, sortOrder = 1),
            ),
        )
        val result = useCase(newCourse)

        assertTrue(result.conflicts.isEmpty())
        assertEquals(1, repo.upserted.size)
    }

    @Test
    fun `semester 为 null 时跨全部学期检测冲突`() = runBlocking {
        // 已有课在某个学期里
        val existing = listOf(course(id = 10L, semester = "2024-2025-2", dayOfWeek = 1, startTime = 540, endTime = 600))
        val repo = FakeTimetableRepository(existing)
        val useCase = UpsertCourseUseCase(repo)

        // 新课**没有归学期** → 撞了任何学期的课都要报
        val newCourse = course(id = 0L, semester = null, dayOfWeek = 1, startTime = 555, endTime = 615)
        try {
            useCase(newCourse)
            fail("expected CourseConflictException")
        } catch (e: CourseConflictException) {
            assertEquals(1, e.conflict.conflicts.size)
        }
    }

    @Test
    fun `写入走的是整门课保存（含课时）`() = runBlocking {
        val repo = FakeTimetableRepository(emptyList())
        val useCase = UpsertCourseUseCase(repo)

        val newCourse = course(
            id = 0L,
            dayOfWeek = 1,
            startTime = 540,
            endTime = 600,
            sessions = listOf(
                session(dayOfWeek = 1, startTime = 540, endTime = 600, sortOrder = 0),
                session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 1),
            ),
        )
        useCase(newCourse)

        assertEquals(1, repo.upserted.size)
        // 整门课保存 —— 课时一起进去了
        assertEquals(2, repo.upserted[0].sessions.size)
    }
}
