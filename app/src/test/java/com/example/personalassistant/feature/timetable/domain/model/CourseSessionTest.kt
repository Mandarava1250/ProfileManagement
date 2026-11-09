package com.example.personalassistant.feature.timetable.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28 P1：CourseSession 的参数校验与区间相交判断
 */
class CourseSessionTest {

    private fun session(
        id: Long = 0L,
        courseId: Long = 1L,
        dayOfWeek: Int = 1,
        startTime: Int = 8 * 60,
        endTime: Int = 9 * 60 + 40,
        sortOrder: Int = 0,
    ) = CourseSession(
        id = id,
        courseId = courseId,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        sortOrder = sortOrder,
    )

    // ===== init 校验 =====

    @Test
    fun `dayOfWeek below range throws`() {
        assertThrowsIllegalArgument { session(dayOfWeek = 0) }
    }

    @Test
    fun `dayOfWeek above range throws`() {
        assertThrowsIllegalArgument { session(dayOfWeek = 8) }
    }

    @Test
    fun `startTime below range throws`() {
        assertThrowsIllegalArgument { session(startTime = -1) }
    }

    @Test
    fun `startTime above range throws`() {
        assertThrowsIllegalArgument { session(startTime = 1440) }
    }

    @Test
    fun `endTime below range throws`() {
        assertThrowsIllegalArgument { session(startTime = 480, endTime = -1) }
    }

    @Test
    fun `endTime above range throws`() {
        assertThrowsIllegalArgument { session(startTime = 480, endTime = 1440) }
    }

    @Test
    fun `endTime equal to startTime throws`() {
        assertThrowsIllegalArgument { session(startTime = 480, endTime = 480) }
    }

    @Test
    fun `endTime before startTime throws`() {
        assertThrowsIllegalArgument { session(startTime = 600, endTime = 540) }
    }

    @Test
    fun `valid session does not throw`() {
        val s = session(dayOfWeek = 7, startTime = 0, endTime = 1439)
        assertEquals(7, s.dayOfWeek)
        assertEquals(0, s.startTime)
        assertEquals(1439, s.endTime)
    }

    // ===== durationMinutes =====

    @Test
    fun `durationMinutes is end minus start`() {
        assertEquals(100, session(startTime = 480, endTime = 580).durationMinutes)
    }

    // ===== overlaps =====

    @Test
    fun `overlaps same day intersecting returns true`() {
        val a = session(dayOfWeek = 1, startTime = 480, endTime = 580)
        val b = session(dayOfWeek = 1, startTime = 555, endTime = 615)
        assertTrue(a.overlaps(b))
        assertTrue(b.overlaps(a))
    }

    @Test
    fun `overlaps same day fully nested returns true`() {
        val outer = session(dayOfWeek = 1, startTime = 480, endTime = 720)
        val inner = session(dayOfWeek = 1, startTime = 540, endTime = 600)
        assertTrue(outer.overlaps(inner))
        assertTrue(inner.overlaps(outer))
    }

    @Test
    fun `overlaps same day back-to-back returns false`() {
        // 首尾相接不算相交 —— 08:00-09:40 与 09:40-11:00 可以连排
        val a = session(dayOfWeek = 1, startTime = 480, endTime = 580)
        val b = session(dayOfWeek = 1, startTime = 580, endTime = 660)
        assertFalse(a.overlaps(b))
        assertFalse(b.overlaps(a))
    }

    @Test
    fun `overlaps same day disjoint returns false`() {
        val a = session(dayOfWeek = 1, startTime = 480, endTime = 580)
        val b = session(dayOfWeek = 1, startTime = 800, endTime = 900)
        assertFalse(a.overlaps(b))
        assertFalse(b.overlaps(a))
    }

    @Test
    fun `overlaps different day returns false`() {
        // 时间完全一样，但不在同一天 —— 不算冲突
        val a = session(dayOfWeek = 1, startTime = 480, endTime = 580)
        val b = session(dayOfWeek = 2, startTime = 480, endTime = 580)
        assertFalse(a.overlaps(b))
        assertFalse(b.overlaps(a))
    }

    @Test
    fun `overlaps same day same course still reports overlap`() {
        // 同一门课的两个课时互相撞了也是错 —— overlaps 不做「同课排除」，
        // 那是上层「课程内部自相矛盾」检查的事。
        val a = session(courseId = 1L, dayOfWeek = 1, startTime = 480, endTime = 580)
        val b = session(courseId = 1L, dayOfWeek = 1, startTime = 500, endTime = 600)
        assertTrue(a.overlaps(b))
    }

    // ===== helpers =====

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("期望抛出 IllegalArgumentException，但没有抛")
        } catch (e: IllegalArgumentException) {
            // 正常路径
        }
    }
}
