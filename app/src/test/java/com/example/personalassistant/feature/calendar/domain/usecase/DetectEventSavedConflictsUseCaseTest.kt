package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.usecase.fake.FakeCourseDao
import com.example.personalassistant.feature.calendar.domain.usecase.fake.FakeSemesterDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Phase 8 Sub-step 9 Step 4：DetectEventSavedConflictsUseCase 单元测试
 *
 * 覆盖：
 * - 入参校验：event.id == 0 抛 IllegalArgumentException
 * - 整天事件 → 短路
 * - 无当前学期 → 短路
 * - 学期外 → 短路
 * - 真实冲突（同 dayOfWeek + 时段重叠 + 同周）
 * - 部分重叠 / 接边不冲突 / 不同 dayOfWeek 不冲突
 * - 周次规则过滤
 * - **核心：excludeId = event.id**，自己不与自己冲突
 */
class DetectEventSavedConflictsUseCaseTest {

    private val DAY = 24L * 60 * 60 * 1000

    private val semesterStart: Long =
        LocalDateTime.of(2026, 9, 7, 0, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val semesterEnd: Long = semesterStart + 18L * 7 * DAY
    private val semesterName = "2026-Fall"

    private fun mondayAt(hour: Int, minute: Int = 0): Long =
        semesterStart + hour * 3600_000L + minute * 60_000L

    private fun event(
        id: Long = 1L,  // Saved 必须 > 0
        startHour: Int = 9,
        endHour: Int = 10,
        isAllDay: Boolean = false,
    ): Event = Event(
        id = id,
        title = "test",
        date = semesterStart,
        isAllDay = isAllDay,
        startTime = if (isAllDay) null else mondayAt(startHour),
        endTime = if (isAllDay) null else mondayAt(endHour),
    )

    private fun courseEntity(
        id: Long = 100L,
        dayOfWeek: Int = 1,
        startMin: Int = 540,
        endMin: Int = 600,
        semester: String = semesterName,
        weeks: String? = null,
    ) = CourseEntity(
        id = id,
        name = "高数",
        dayOfWeek = dayOfWeek,
        startTime = startMin,
        endTime = endMin,
        color = 0xFFE57373.toInt(),
        semester = semester,
        weeks = weeks,
    )

    private fun buildUseCase(
        courses: List<CourseEntity> = emptyList(),
        semester: SemesterEntity? = SemesterEntity(
            name = semesterName,
            startDate = semesterStart,
            endDate = semesterEnd,
            isCurrent = true,
        ),
    ): DetectEventSavedConflictsUseCase {
        val courseDao = FakeCourseDao().apply { seed(courses) }
        val semesterDao = FakeSemesterDao(current = semester)
        return DetectEventSavedConflictsUseCase(courseDao, semesterDao)
    }

    // ===== 入参校验 =====

    @Test
    fun `event id 0 throws IllegalArgumentException`() = runTest {
        val useCase = buildUseCase()
        try {
            useCase.invoke(event(id = 0L))
            fail("expected IllegalArgumentException for event.id == 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "should mention id > 0 requirement",
                e.message!!.contains("event.id > 0"),
            )
        }
    }

    @Test
    fun `negative event id throws IllegalArgumentException`() = runTest {
        val useCase = buildUseCase()
        try {
            useCase.invoke(event(id = -5L))
            fail("expected IllegalArgumentException for negative id")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("event.id > 0"))
        }
    }

    // ===== 整天事件 =====

    @Test
    fun `all-day event returns empty conflicts`() = runTest {
        val result = buildUseCase().invoke(event(isAllDay = true))
        assertTrue(result.conflictingCourses.isEmpty())
        assertTrue(result.isAllDay)
    }

    // ===== 无学期 / 学期外 =====

    @Test
    fun `no semester returns empty conflicts`() = runTest {
        val result = buildUseCase(semester = null).invoke(event())
        assertTrue(result.conflictingCourses.isEmpty())
    }

    @Test
    fun `date after semester end returns empty conflicts`() = runTest {
        val after = Event(
            id = 1L,
            title = "t",
            date = semesterEnd + 7 * DAY,
            startTime = semesterEnd + 7 * DAY + 9 * 3600_000L,
            endTime = semesterEnd + 7 * DAY + 10 * 3600_000L,
        )
        val result = buildUseCase().invoke(after)
        assertTrue(result.conflictingCourses.isEmpty())
    }

    // ===== 真实冲突 =====

    @Test
    fun `overlapping course is detected`() = runTest {
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 200L)),
        ).invoke(event(id = 1L, startHour = 9, endHour = 10))
        assertEquals(1, result.conflictingCourses.size)
        assertEquals(200L, result.conflictingCourses[0].id)
    }

    @Test
    fun `touching edge does not conflict`() = runTest {
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 200L, startMin = 540, endMin = 600)),
        ).invoke(event(id = 1L, startHour = 10, endHour = 11))
        assertTrue(result.conflictingCourses.isEmpty())
    }

    @Test
    fun `different dayOfWeek does not conflict`() = runTest {
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 200L, dayOfWeek = 2)),
        ).invoke(event(id = 1L))
        assertTrue(result.conflictingCourses.isEmpty())
    }

    // ===== 核心：Saved 排除自身 =====

    @Test
    fun `self is excluded - course with same id as event is filtered out`() = runTest {
        // event.id=1, courses 里也有 id=1（假设是历史快照），应该被排除
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 1L, startMin = 540, endMin = 600)),
        ).invoke(event(id = 1L))
        assertEquals("Saved 模式应排除 event 自身", 0, result.conflictingCourses.size)
    }

    @Test
    fun `self excluded but other conflicts still detected`() = runTest {
        // id=1 的 course 是自己；id=2 的 course 是另一个冲突
        val result = buildUseCase(
            courses = listOf(
                courseEntity(id = 1L, startMin = 540, endMin = 600),
                courseEntity(id = 2L, startMin = 545, endMin = 605),
            ),
        ).invoke(event(id = 1L))
        assertEquals(1, result.conflictingCourses.size)
        assertEquals(2L, result.conflictingCourses[0].id)
    }

    // ===== 周次过滤 =====

    @Test
    fun `weeks rule excludes current week - filtered out`() = runTest {
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 200L, weeks = "2-9")),
        ).invoke(event(id = 1L))
        assertTrue(result.conflictingCourses.isEmpty())
    }

    @Test
    fun `weeks rule includes current week - conflict detected`() = runTest {
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 200L, weeks = "1-9")),
        ).invoke(event(id = 1L))
        assertEquals(1, result.conflictingCourses.size)
    }

    // ===== 多冲突 =====

    @Test
    fun `multiple conflicts returns all of them`() = runTest {
        val result = buildUseCase(
            courses = listOf(
                courseEntity(id = 200L, startMin = 540, endMin = 600),
                courseEntity(id = 201L, startMin = 545, endMin = 605),
            ),
        ).invoke(event(id = 1L))
        assertEquals(2, result.conflictingCourses.size)
    }
}