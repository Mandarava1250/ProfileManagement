package com.example.personalassistant.feature.calendar.data.mapper

import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.CourseWithSessionRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28 P6：日历「按天查课」的组装逻辑
 *
 * ## 为什么要测这个
 *
 * 日历原来按 `courses.dayOfWeek`（**主时段镜像**）查课：
 * 一门课「周一 + 周三」两个课时时，周三的日历上**看不到这门课**（漏课）。
 * 改成 JOIN `course_sessions` 之后，按天查出来的行必须能被组装成「这一天的课程」。
 *
 * 这里守住：
 * - 非主时段的那天也能查到这门课
 * - 投影后课程的三列镜像 = **这一天**的第一个课时（UI 读 startTime 才不会读错）
 * - 没有课时记录的老数据仍然按主时段镜像兜底
 * - 同一门课同一天两个课时 → 一门课一条记录，课时列表里有两个
 */
class CalendarCourseMappersTest {

    @Test
    fun `主时段在周一课时也在周三时 周三能查到这门课`() {
        val course = courseEntity(id = 1L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45)
        // 周三那行（JOIN 出来的）
        val wednesdayRow = row(
            course = course,
            sessionId = 2L,
            dayOfWeek = 3,
            startTime = 10 * 60,
            endTime = 10 * 60 + 45,
        )

        val wednesdayCourses = buildDayCourses(
            joinedRows = listOf(wednesdayRow),
            mirroredCourses = emptyList(),
            dayOfWeek = 3,
        )

        assertEquals("周三这一天必须有这门课（原来按主时段查是查不到的）", 1, wednesdayCourses.size)
        val projected = wednesdayCourses.single()
        assertEquals(1L, projected.id)
        assertEquals("投影后的星期几 = 这一天", 3, projected.dayOfWeek)
        assertEquals("投影后的开始时间 = 这一天课时的开始时间", 10 * 60, projected.startTime)
        assertEquals("投影后的结束时间 = 这一天课时的结束时间", 10 * 60 + 45, projected.endTime)
        assertEquals("课时列表只留这一天的那些", 1, projected.sessions.size)
    }

    @Test
    fun `同一天两个课时仍是一门课一条记录`() {
        val course = courseEntity(id = 1L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45)
        val rows = listOf(
            row(course, sessionId = 2L, dayOfWeek = 3, startTime = 10 * 60, endTime = 10 * 60 + 45),
            row(course, sessionId = 3L, dayOfWeek = 3, startTime = 14 * 60, endTime = 14 * 60 + 45),
        )

        val courses = buildDayCourses(rows, emptyList(), dayOfWeek = 3)

        assertEquals("一门课一条记录", 1, courses.size)
        val projected = courses.single()
        assertEquals("这一天的两个课时都在", 2, projected.sessions.size)
        assertEquals("三列镜像 = 这一天第一个课时的开始时间", 10 * 60, projected.startTime)
    }

    @Test
    fun `没有课时记录的老课程按主时段镜像兜底`() {
        val legacy = courseEntity(id = 9L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45)

        // 周一：镜像查得到 → 兜底出一个课时
        val monday = buildDayCourses(emptyList(), listOf(legacy), dayOfWeek = 1)
        assertEquals("老数据也要出现在日历上", 1, monday.size)
        assertEquals(8 * 60, monday.single().startTime)
        assertEquals("兜底课时挂在课程身上", 9L, monday.single().sessions.single().courseId)

        // 周三：既没有课时、镜像也不是周三 → 不出现
        val wednesday = buildDayCourses(emptyList(), listOf(legacy), dayOfWeek = 3)
        assertTrue("这一天没有课就不该出现", wednesday.isEmpty())
    }

    @Test
    fun `JOIN 出来的课与镜像查出来的老课不会重复`() {
        val withSession = courseEntity(id = 1L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45)
        val joinedRow = row(withSession, sessionId = 2L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45)

        // 同一门课既出现在 JOIN 结果里，也出现在镜像结果里（它确实有课时）
        val courses = buildDayCourses(
            joinedRows = listOf(joinedRow),
            mirroredCourses = listOf(withSession),
            dayOfWeek = 1,
        )

        assertEquals("同一门课只算一条", 1, courses.size)
    }

    @Test
    fun `按星期几分桶时课在有课时的那些天`() {
        val course = courseEntity(id = 1L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45)
        val rows = listOf(
            row(course, sessionId = 2L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45),
            row(course, sessionId = 3L, dayOfWeek = 3, startTime = 10 * 60, endTime = 10 * 60 + 45),
        )

        val byDay = buildCoursesByDay(rows, emptyList())

        assertEquals("周一有课", 1, byDay.getValue(1).size)
        assertEquals("周三也有课", 1, byDay.getValue(3).size)
        assertTrue("周二没课", byDay.getValue(2).isEmpty())
        // 1..7 必须补齐（下游靠它给当月每一天补 entry）
        assertEquals(7, byDay.size)
    }

    // ===== 测试数据 =====

    private fun courseEntity(
        id: Long,
        dayOfWeek: Int,
        startTime: Int,
        endTime: Int,
    ): CourseEntity = CourseEntity(
        id = id,
        name = "高等数学 A",
        teacher = "李老师",
        location = "教三 201",
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        color = 0xFF3F7DE0.toInt(),
        semester = "2026-2027-1",
    )

    private fun row(
        course: CourseEntity,
        sessionId: Long,
        dayOfWeek: Int,
        startTime: Int,
        endTime: Int,
    ): CourseWithSessionRow = CourseWithSessionRow(
        course = course,
        sessionId = sessionId,
        sessionDayOfWeek = dayOfWeek,
        sessionStartTime = startTime,
        sessionEndTime = endTime,
        sessionSortOrder = 0,
        // Phase 28 P10：课时自己的教师 / 地点（这里跟课程级一致，等价于迁移后的回填结果）
        sessionTeacher = course.teacher,
        sessionLocation = course.location,
    )
}
