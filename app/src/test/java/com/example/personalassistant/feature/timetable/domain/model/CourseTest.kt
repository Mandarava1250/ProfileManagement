package com.example.personalassistant.feature.timetable.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Course.appliesToWeek 与 Course.conflictsWith 的纯逻辑测试
 *
 * ## Phase 28：conflictsWith 改成按课时判断
 *
 * 「冲突」= 我的某个课时与对方的某个课时重叠。
 * 没有课时记录的一方用主时段镜像兜底出一个单课时（[Course.effectiveSessions]）。
 */
class CourseTest {

    private fun course(
        id: Long = 1L,
        name: String = "高数",
        dayOfWeek: Int = 1,
        startTime: Int = 8 * 60,
        endTime: Int = 9 * 60 + 45,
        semester: String? = "2024-2025-1",
        weeks: String? = null,
        sessions: List<CourseSession> = emptyList(),
    ) = Course(
        id = id,
        name = name,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        color = 0xFFE57373.toInt(),
        semester = semester,
        weeks = weeks,
        sessions = sessions,
    )

    private fun session(
        dayOfWeek: Int = 1,
        startTime: Int = 8 * 60,
        endTime: Int = 9 * 60 + 45,
        sortOrder: Int = 0,
    ) = CourseSession(
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        sortOrder = sortOrder,
    )

    // ===== appliesToWeek =====

    @Test
    fun `appliesToWeek returns true when weeks is null`() {
        val c = course(weeks = null)
        assertTrue(c.appliesToWeek(1))
        assertTrue(c.appliesToWeek(10))
    }

    @Test
    fun `appliesToWeek returns true when weeks is empty`() {
        val c = course(weeks = "")
        assertTrue(c.appliesToWeek(5))
    }

    @Test
    fun `appliesToWeek handles single range 1-16`() {
        val c = course(weeks = "1-16")
        assertTrue(c.appliesToWeek(1))
        assertTrue(c.appliesToWeek(8))
        assertTrue(c.appliesToWeek(16))
        assertFalse(c.appliesToWeek(17))
        assertFalse(c.appliesToWeek(0))
    }

    @Test
    fun `appliesToWeek handles discrete list 1,3,5`() {
        val c = course(weeks = "1,3,5")
        assertTrue(c.appliesToWeek(1))
        assertTrue(c.appliesToWeek(3))
        assertTrue(c.appliesToWeek(5))
        assertFalse(c.appliesToWeek(2))
        assertFalse(c.appliesToWeek(4))
    }

    @Test
    fun `appliesToWeek handles mixed 1-8,10-16`() {
        val c = course(weeks = "1-8,10-16")
        assertTrue(c.appliesToWeek(1))
        assertTrue(c.appliesToWeek(8))
        assertTrue(c.appliesToWeek(10))
        assertTrue(c.appliesToWeek(16))
        assertFalse(c.appliesToWeek(9))
        assertFalse(c.appliesToWeek(17))
    }

    @Test
    fun `appliesToWeek ignores whitespace`() {
        val c = course(weeks = " 1 - 8 , 10 - 16 ")
        assertTrue(c.appliesToWeek(5))
        assertTrue(c.appliesToWeek(12))
        assertFalse(c.appliesToWeek(9))
    }

    @Test
    fun `appliesToWeek invalid tokens return false`() {
        val c = course(weeks = "abc,1-8")
        assertTrue(c.appliesToWeek(5))
        assertFalse(c.appliesToWeek(20))
    }

    // ===== conflictsWith =====

    @Test
    fun `conflictsWith same course returns false`() {
        val c = course(id = 1L, startTime = 540, endTime = 600)
        assertFalse(c.conflictsWith(c.copy()))
    }

    @Test
    fun `conflictsWith different courses same id returns false (same course)`() {
        val a = course(id = 1L, dayOfWeek = 1, startTime = 540, endTime = 600)
        val b = course(id = 1L, dayOfWeek = 1, startTime = 540, endTime = 600)
        assertFalse(a.conflictsWith(b))
    }

    @Test
    fun `conflictsWith different id same day overlap returns true`() {
        val a = course(id = 1L, dayOfWeek = 1, startTime = 540, endTime = 600)
        val b = course(id = 2L, dayOfWeek = 1, startTime = 555, endTime = 615)
        assertTrue(a.conflictsWith(b))
    }

    @Test
    fun `conflictsWith different day returns false`() {
        val a = course(id = 1L, dayOfWeek = 1, startTime = 540, endTime = 600)
        val b = course(id = 2L, dayOfWeek = 2, startTime = 540, endTime = 600)
        assertFalse(a.conflictsWith(b))
    }

    @Test
    fun `conflictsWith different semester returns false`() {
        val a = course(id = 1L, dayOfWeek = 1, semester = "2024-2025-1", startTime = 540, endTime = 600)
        val b = course(id = 2L, dayOfWeek = 1, semester = "2024-2025-2", startTime = 540, endTime = 600)
        assertFalse(a.conflictsWith(b))
    }

    @Test
    fun `conflictsWith back-to-back courses returns false`() {
        val a = course(id = 1L, dayOfWeek = 1, startTime = 540, endTime = 600)
        val b = course(id = 2L, dayOfWeek = 1, startTime = 600, endTime = 660)
        assertFalse(a.conflictsWith(b))
    }

    @Test
    fun `conflictsWith fully nested returns true`() {
        val outer = course(id = 1L, dayOfWeek = 1, startTime = 540, endTime = 720)
        val inner = course(id = 2L, dayOfWeek = 1, startTime = 600, endTime = 660)
        assertTrue(outer.conflictsWith(inner))
        assertTrue(inner.conflictsWith(outer))
    }

    // ===== Phase 28：多课时语义 =====

    @Test
    fun `effectiveSessions falls back to mirror when there are no sessions`() {
        val c = course(dayOfWeek = 3, startTime = 480, endTime = 540)
        assertEquals(1, c.effectiveSessions.size)
        val s = c.effectiveSessions[0]
        assertEquals(3, s.dayOfWeek)
        assertEquals(480, s.startTime)
        assertEquals(540, s.endTime)
        assertEquals(1L, s.courseId)
    }

    @Test
    fun `effectiveSessions returns the real sessions when present`() {
        val c = course(
            sessions = listOf(
                session(dayOfWeek = 1, startTime = 480, endTime = 540, sortOrder = 0),
                session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 1),
            ),
        )
        assertEquals(2, c.effectiveSessions.size)
        assertEquals(1, c.effectiveSessions[0].dayOfWeek)
        assertEquals(3, c.effectiveSessions[1].dayOfWeek)
    }

    @Test
    fun `conflictsWith detects overlap on the second session`() {
        // 我：周一 8-9 + 周三 9-10
        val mine = course(
            id = 1L,
            dayOfWeek = 1,
            startTime = 480,
            endTime = 540,
            sessions = listOf(
                session(dayOfWeek = 1, startTime = 480, endTime = 540, sortOrder = 0),
                session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 1),
            ),
        )
        // 对方只有周三 9-10
        val other = course(id = 2L, dayOfWeek = 3, startTime = 540, endTime = 600)
        assertTrue(mine.conflictsWith(other))
        assertTrue(other.conflictsWith(mine))
    }

    @Test
    fun `conflictsWith ignores non-overlapping sessions of the same course`() {
        // 我：周一 8-9 + 周三 9-10
        val mine = course(
            id = 1L,
            dayOfWeek = 1,
            startTime = 480,
            endTime = 540,
            sessions = listOf(
                session(dayOfWeek = 1, startTime = 480, endTime = 540, sortOrder = 0),
                session(dayOfWeek = 3, startTime = 540, endTime = 600, sortOrder = 1),
            ),
        )
        // 对方：周五 8-9（跟我的两个时段都不撞）
        val other = course(id = 2L, dayOfWeek = 5, startTime = 480, endTime = 540)
        assertFalse(mine.conflictsWith(other))
    }

    @Test
    fun `conflictsWith uses mirror when the other side has no sessions`() {
        val mine = course(id = 1L, dayOfWeek = 2, startTime = 480, endTime = 540)
        val other = course(id = 2L, dayOfWeek = 2, startTime = 500, endTime = 560)
        assertTrue(mine.conflictsWith(other))
    }
}