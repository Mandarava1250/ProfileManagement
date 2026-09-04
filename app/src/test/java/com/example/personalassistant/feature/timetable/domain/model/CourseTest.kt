package com.example.personalassistant.feature.timetable.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Course.appliesToWeek 与 Course.conflictsWith 的纯逻辑测试
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
    ) = Course(
        id = id,
        name = name,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        color = 0xFFE57373.toInt(),
        semester = semester,
        weeks = weeks,
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
}