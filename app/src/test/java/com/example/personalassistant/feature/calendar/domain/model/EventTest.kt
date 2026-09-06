package com.example.personalassistant.feature.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7c：Event 数据类校验 + 计算属性测试
 *
 * 不依赖 Room，纯 JVM 测试。
 */
class EventTest {

    private fun event(
        id: Long = 1L,
        title: String = "开会",
        date: Long = 1_700_000_000_000L,
        isAllDay: Boolean = false,
        startTime: Long? = date + 9 * 60 * 60 * 1000L,    // 当天 09:00
        endTime: Long? = date + 10 * 60 * 60 * 1000L,      // 当天 10:00
        color: Int = Event.DEFAULT_COLOR,
        reminder: Int? = null,
        note: String? = null,
        category: String? = null,
        linkedTaskId: Long? = null,
    ) = Event(
        id = id,
        title = title,
        date = date,
        isAllDay = isAllDay,
        startTime = startTime,
        endTime = endTime,
        color = color,
        reminder = reminder,
        note = note,
        category = category,
        linkedTaskId = linkedTaskId,
    )

    // ===== init 校验 =====

    @Test(expected = IllegalArgumentException::class)
    fun `blank title throws IllegalArgumentException`() {
        event(title = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `whitespace title throws IllegalArgumentException`() {
        event(title = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative reminder throws IllegalArgumentException`() {
        event(reminder = -1)
    }

    @Test
    fun `null reminder is valid`() {
        val e = event(reminder = null)
        assertEquals(null, e.reminder)
    }

    @Test
    fun `zero reminder is valid`() {
        val e = event(reminder = 0)
        assertEquals(0, e.reminder)
    }

    // ===== 计算属性：isEffectivelyAllDay =====

    @Test
    fun `isEffectivelyAllDay true when isAllDay flag is true`() {
        assertTrue(event(isAllDay = true, startTime = null, endTime = null).isEffectivelyAllDay)
    }

    @Test
    fun `isEffectivelyAllDay true when both startTime and endTime are null`() {
        assertTrue(event(isAllDay = false, startTime = null, endTime = null).isEffectivelyAllDay)
    }

    @Test
    fun `isEffectivelyAllDay false when only startTime is null but endTime exists`() {
        // 仅 endTime 无 startTime 视为有起止（防御性，正常输入不应出现）
        assertFalse(event(startTime = null, endTime = 100L).isEffectivelyAllDay)
    }

    @Test
    fun `isEffectivelyAllDay false when both startTime and endTime are non-null`() {
        assertFalse(event().isEffectivelyAllDay)
    }

    // ===== 计算属性：hasTimeRange =====

    @Test
    fun `hasTimeRange true only when both times present and not all-day`() {
        assertTrue(event().hasTimeRange)
        assertFalse(event(isAllDay = true).hasTimeRange)
        assertFalse(event(startTime = null).hasTimeRange)
        assertFalse(event(endTime = null).hasTimeRange)
    }

    // ===== 默认值 =====

    @Test
    fun `default color is Material 3 primary`() {
        assertEquals(0xFF6750A4.toInt(), Event.DEFAULT_COLOR)
        assertEquals(Event.DEFAULT_COLOR, event().color)
    }
}