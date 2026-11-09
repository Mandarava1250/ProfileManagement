package com.example.personalassistant.feature.timetable.presentation

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.CourseSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28 P5：课表「一个课时 = 一个块」的摊平逻辑
 *
 * 渲染层（Compose）测不了，但**摊平**这一步是纯函数 —— 这里守住它的语义：
 * - 一门课三个课时 → 三个块（course 相同、session 不同）
 * - 没有课时记录的老数据 → 兜底出一个块（不能让课从课表上消失）
 * - 这一周不上课的课 → 不占格子
 * - 排序：先按星期几，再按开始时间
 */
class CourseSlotTest {

    @Test
    fun `一门课三个课时摊平成三个块`() {
        val course = course(
            id = 1L,
            sessions = listOf(
                session(id = 11L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45),
                session(id = 12L, dayOfWeek = 3, startTime = 10 * 60, endTime = 10 * 60 + 45),
                session(id = 13L, dayOfWeek = 5, startTime = 14 * 60, endTime = 14 * 60 + 45),
            ),
        )

        val slots = listOf(course).toCourseSlots(weekNumber = 1)

        assertEquals("三个课时应摊平成三个块", 3, slots.size)
        // 同一门课 → course 相同，块的颜色也就相同（用户据此看出是同一门课）
        assertTrue(slots.all { it.course.id == 1L })
        assertEquals(listOf(1, 3, 5), slots.map { it.session.dayOfWeek })
        assertEquals(listOf(11L, 12L, 13L), slots.map { it.session.id })
    }

    @Test
    fun `没有课时记录的老课程兜底出一个块`() {
        // 老数据 / 旧备份导入的课：sessions 为空 → 用主时段镜像兜底
        val course = course(id = 2L, sessions = emptyList())

        val slots = listOf(course).toCourseSlots(weekNumber = 1)

        assertEquals("兜底也要占一个块，否则这门课从课表上消失了", 1, slots.size)
        assertEquals(2L, slots.single().course.id)
        assertEquals("兜底课时的星期几 = 主时段镜像", 1, slots.single().session.dayOfWeek)
        assertEquals(8 * 60, slots.single().session.startTime)
        assertEquals("兜底出来的课时 id == 0（没落库）", 0L, slots.single().session.id)
    }

    @Test
    fun `这一周不上课的课不占格子`() {
        val course = course(id = 3L, weeks = "1-8")

        assertEquals("第 8 周要上课", 1, listOf(course).toCourseSlots(weekNumber = 8).size)
        assertEquals("第 9 周不上课 → 不占格子", 0, listOf(course).toCourseSlots(weekNumber = 9).size)
    }

    @Test
    fun `块按星期几再按开始时间排序`() {
        val monday = course(
            id = 1L,
            sessions = listOf(
                session(id = 1L, dayOfWeek = 1, startTime = 14 * 60, endTime = 14 * 60 + 45),
                session(id = 2L, dayOfWeek = 1, startTime = 8 * 60, endTime = 8 * 60 + 45),
            ),
        )
        val wednesday = course(
            id = 2L,
            sessions = listOf(
                session(id = 3L, dayOfWeek = 3, startTime = 10 * 60, endTime = 10 * 60 + 45),
            ),
        )

        val slots = listOf(monday, wednesday).toCourseSlots(weekNumber = 1)

        assertEquals(
            listOf(listOf(1, 8 * 60), listOf(1, 14 * 60), listOf(3, 10 * 60)),
            slots.map { listOf(it.session.dayOfWeek, it.session.startTime) },
        )
    }

    // ===== 测试数据 =====

    private fun course(
        id: Long,
        weeks: String? = null,
        sessions: List<CourseSession> = emptyList(),
    ): Course = Course(
        id = id,
        name = "高等数学 A",
        dayOfWeek = 1,
        startTime = 8 * 60,
        endTime = 8 * 60 + 45,
        color = 0xFF6200EE.toInt(),
        semester = "2025-2026-1",
        weeks = weeks,
        sessions = sessions,
    )

    private fun session(
        id: Long,
        dayOfWeek: Int,
        startTime: Int,
        endTime: Int,
    ): CourseSession = CourseSession(
        id = id,
        courseId = 1L,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        sortOrder = 0,
    )
}
