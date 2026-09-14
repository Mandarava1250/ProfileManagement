package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.EventConflictInfo
import com.example.personalassistant.feature.calendar.domain.usecase.fake.FakeCourseDao
import com.example.personalassistant.feature.calendar.domain.usecase.fake.FakeSemesterDao
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Phase 8 Sub-step 9 Step 4：DetectEventLiveConflictsUseCase 单元测试
 *
 * 覆盖：
 * - 整天事件 → isAllDay=true, conflictingCourses=empty
 * - 无当前学期 → 短路
 * - 学期外 → 短路
 * - 真实冲突（同 dayOfWeek + 时段重叠 + 同周）
 * - 部分重叠 / 接边不冲突 / 不同 dayOfWeek 不冲突
 * - 周次规则过滤（weeks="2-9" + 当前周 1 → 不冲突）
 * - excludeId = -1L（不排除任何）
 */
class DetectEventLiveConflictsUseCaseTest {

    private val DAY = 24L * 60 * 60 * 1000

    // 锚 2026-09-07 周一（第 1 周）
    private val semesterStart: Long =
        LocalDateTime.of(2026, 9, 7, 0, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val semesterEnd: Long = semesterStart + 18L * 7 * DAY  // 18 周
    private val semesterName = "2026-Fall"

    private fun mondayAt(hour: Int, minute: Int = 0): Long =
        semesterStart + hour * 3600_000L + minute * 60_000L

    private fun event(
        id: Long = 0L,
        startHour: Int = 9,
        startMin: Int = 0,
        endHour: Int = 10,
        endMin: Int = 0,
        isAllDay: Boolean = false,
    ): Event = Event(
        id = id,
        title = "test",
        date = semesterStart,
        isAllDay = isAllDay,
        startTime = if (isAllDay) null else mondayAt(startHour, startMin),
        endTime = if (isAllDay) null else mondayAt(endHour, endMin),
    )

    private fun courseEntity(
        id: Long = 1L,
        dayOfWeek: Int = 1,
        startMin: Int = 540,  // 9:00
        endMin: Int = 600,    // 10:00
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
    ): DetectEventLiveConflictsUseCase {
        val courseDao = FakeCourseDao().apply { seed(courses) }
        val semesterDao = FakeSemesterDao(current = semester)
        return DetectEventLiveConflictsUseCase(courseDao, semesterDao)
    }

    // ===== 整天事件 =====

    @Test
    fun `all-day event returns empty list and isAllDay true`() = runTest {
        val result = buildUseCase().invoke(event(isAllDay = true))
        assertTrue(result.conflictingCourses.isEmpty())
        assertTrue(result.isAllDay)
        assertNull(result.weekNumber)
        assertFalse(result.hasConflict)
    }

    // ===== 无学期 =====

    @Test
    fun `no current semester returns slice with empty conflicts`() = runTest {
        val result = buildUseCase(semester = null).invoke(event())
        assertTrue(result.conflictingCourses.isEmpty())
        assertNotNull(result.timeSlice)
        assertNull(result.weekNumber)
        assertFalse(result.isWithinSemester)
    }

    // ===== 学期外 =====

    @Test
    fun `date before semester start returns empty conflicts`() = runTest {
        val before = Event(
            id = 1L,
            title = "t",
            date = semesterStart - 14 * DAY,  // 2 周前
            startTime = semesterStart - 14 * DAY + 9 * 3600_000L,
            endTime = semesterStart - 14 * DAY + 10 * 3600_000L,
        )
        val result = buildUseCase().invoke(before)
        assertTrue(result.conflictingCourses.isEmpty())
        assertNotNull(result.timeSlice)
        assertNull(result.weekNumber)
    }

    @Test
    fun `date after semester end returns empty conflicts - endDate boundary`() = runTest {
        val after = Event(
            id = 1L,
            title = "t",
            date = semesterEnd + 7 * DAY,  // 学期结束后 1 周
            startTime = semesterEnd + 7 * DAY + 9 * 3600_000L,
            endTime = semesterEnd + 7 * DAY + 10 * 3600_000L,
        )
        val result = buildUseCase().invoke(after)
        assertTrue(result.conflictingCourses.isEmpty())
        assertNull(result.weekNumber)
    }

    // ===== 真实冲突 =====

    @Test
    fun `exact overlap returns conflicting course`() = runTest {
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 100L)),
        ).invoke(event(startHour = 9, endHour = 10))
        assertEquals(1, result.conflictingCourses.size)
        assertEquals(100L, result.conflictingCourses[0].id)
        assertTrue(result.hasConflict)
        assertEquals(1, result.weekNumber)
    }

    @Test
    fun `partial overlap returns conflicting course`() = runTest {
        // event 9:30-10:30, course 9:00-10:00 → 重叠 9:30-10:00
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 100L, startMin = 540, endMin = 600)),
        ).invoke(event(startHour = 9, startMin = 30, endHour = 10, endMin = 30))
        assertEquals(1, result.conflictingCourses.size)
    }

    @Test
    fun `touching edge does not conflict - 10 00 to 11 00 vs 09 00 to 10 00`() = runTest {
        // event 10:00-11:00, course 9:00-10:00 → 严格重叠不接边
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 100L, startMin = 540, endMin = 600)),
        ).invoke(event(startHour = 10, endHour = 11))
        assertTrue("Edge touching should not conflict", result.conflictingCourses.isEmpty())
    }

    @Test
    fun `different dayOfWeek does not conflict`() = runTest {
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 100L, dayOfWeek = 2)),  // 周二
        ).invoke(event())  // event 在周一
        assertTrue(result.conflictingCourses.isEmpty())
    }

    @Test
    fun `different semester does not conflict`() = runTest {
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 100L, semester = "2025-Spring")),
        ).invoke(event())
        assertTrue(result.conflictingCourses.isEmpty())
    }

    // ===== 周次规则过滤 =====

    @Test
    fun `course with weeks rule matching current week still conflicts`() = runTest {
        // weeks = "1,3,5"，第 1 周匹配 ✓
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 100L, weeks = "1,3,5")),
        ).invoke(event())
        assertEquals(1, result.conflictingCourses.size)
    }

    @Test
    fun `course with weeks rule excluding current week is filtered out`() = runTest {
        // event 在第 1 周，course weeks="2-9" 不包含第 1 周
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 100L, weeks = "2-9")),
        ).invoke(event())
        assertTrue("Course weeks=2-9 should not conflict with week 1", result.conflictingCourses.isEmpty())
    }

    @Test
    fun `course with null weeks applies to all weeks`() = runTest {
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 100L, weeks = null)),
        ).invoke(event())
        assertEquals(1, result.conflictingCourses.size)
    }

    // ===== 多个冲突 =====

    @Test
    fun `multiple conflicts returns all of them`() = runTest {
        val result = buildUseCase(
            courses = listOf(
                courseEntity(id = 100L, startMin = 540, endMin = 600),
                courseEntity(id = 101L, startMin = 545, endMin = 605),
                courseEntity(id = 102L, dayOfWeek = 2),  // 不同天 → 不冲突
            ),
        ).invoke(event(startHour = 9, endHour = 10))
        assertEquals(2, result.conflictingCourses.size)
        assertTrue(result.conflictingCourses.any { it.id == 100L })
        assertTrue(result.conflictingCourses.any { it.id == 101L })
    }

    // ===== Live excludeId 语义 =====

    @Test
    fun `live mode does NOT exclude event itself - self conflict visible`() = runTest {
        // event 已落库（id=999），与"自己"（id=999）的时段冲突
        // Live 模式 excludeId=-1L，应能检测到自己
        val result = buildUseCase(
            courses = listOf(courseEntity(id = 999L, startMin = 540, endMin = 600)),
        ).invoke(event(id = 999L))
        assertEquals("Live 应该能检测到与自己的旧版本冲突", 1, result.conflictingCourses.size)
        assertEquals(999L, result.conflictingCourses[0].id)
    }
}