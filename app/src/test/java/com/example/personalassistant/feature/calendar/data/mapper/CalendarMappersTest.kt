package com.example.personalassistant.feature.calendar.data.mapper

import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.calendar.domain.model.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 7c：EventEntity ↔ Event 双向映射测试
 */
class CalendarMappersTest {

    private fun entity(
        id: Long = 1L,
        title: String = "生日",
        date: Long = 1_700_000_000_000L,
        isAllDay: Boolean = false,
        startTime: Long? = 1_700_003_240_000L,  // +09:00
        endTime: Long? = 1_700_003_600_000L,    // +10:00
        color: Int = 0xFFE91E63.toInt(),
        reminder: Int? = 30,
        note: String? = "蛋糕",
        category: String? = "生日",
        linkedTaskId: Long? = 42L,
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_001_000L,
    ) = EventEntity(
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
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun domain(
        id: Long = 1L,
        title: String = "生日",
        date: Long = 1_700_000_000_000L,
        isAllDay: Boolean = false,
        startTime: Long? = 1_700_003_240_000L,
        endTime: Long? = 1_700_003_600_000L,
        color: Int = 0xFFE91E63.toInt(),
        reminder: Int? = 30,
        note: String? = "蛋糕",
        category: String? = "生日",
        linkedTaskId: Long? = 42L,
        createdAt: Long = 1_700_000_000_000L,
        updatedAt: Long = 1_700_000_001_000L,
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
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    // ===== toDomain =====

    @Test
    fun `toDomain copies every field`() {
        val src = entity()
        val dst = src.toDomain()

        assertEquals(src.id, dst.id)
        assertEquals(src.title, dst.title)
        assertEquals(src.date, dst.date)
        assertEquals(src.isAllDay, dst.isAllDay)
        assertEquals(src.startTime, dst.startTime)
        assertEquals(src.endTime, dst.endTime)
        assertEquals(src.color, dst.color)
        assertEquals(src.reminder, dst.reminder)
        assertEquals(src.note, dst.note)
        assertEquals(src.category, dst.category)
        assertEquals(src.linkedTaskId, dst.linkedTaskId)
        assertEquals(src.createdAt, dst.createdAt)
        assertEquals(src.updatedAt, dst.updatedAt)
    }

    @Test
    fun `toDomain handles nullable fields`() {
        val src = entity(
            startTime = null,
            endTime = null,
            reminder = null,
            note = null,
            category = null,
            linkedTaskId = null,
        )
        val dst = src.toDomain()

        assertNull(dst.startTime)
        assertNull(dst.endTime)
        assertNull(dst.reminder)
        assertNull(dst.note)
        assertNull(dst.category)
        assertNull(dst.linkedTaskId)
    }

    @Test
    fun `toDomain of all-day event`() {
        val src = entity(isAllDay = true, startTime = null, endTime = null)
        val dst = src.toDomain()

        assertEquals(true, dst.isAllDay)
        assertEquals(true, dst.isEffectivelyAllDay)
        assertEquals(false, dst.hasTimeRange)
    }

    // ===== toEntity =====

    @Test
    fun `toEntity copies every field except updatedAt is refreshed`() {
        val src = domain(updatedAt = 1_700_000_001_000L)
        val before = System.currentTimeMillis()
        val dst = src.toEntity()

        assertEquals(src.id, dst.id)
        assertEquals(src.title, dst.title)
        assertEquals(src.date, dst.date)
        assertEquals(src.isAllDay, dst.isAllDay)
        assertEquals(src.startTime, dst.startTime)
        assertEquals(src.endTime, dst.endTime)
        assertEquals(src.color, dst.color)
        assertEquals(src.reminder, dst.reminder)
        assertEquals(src.note, dst.note)
        assertEquals(src.category, dst.category)
        assertEquals(src.linkedTaskId, dst.linkedTaskId)
        assertEquals(src.createdAt, dst.createdAt)

        // updatedAt 应当被 toEntity 刷新（实际值不固定，但要 ≥ before 且 ≠ 原值）
        assertNotEquals(src.updatedAt, dst.updatedAt)
        assertNotNull(dst.updatedAt)
        assert(dst.updatedAt >= before) { "updatedAt ${dst.updatedAt} 应 >= $before" }
    }

    @Test
    fun `round trip toDomain and back preserves fields`() {
        val src = entity()
        val round = src.toDomain().toEntity()

        assertEquals(src.id, round.id)
        assertEquals(src.title, round.title)
        assertEquals(src.date, round.date)
        assertEquals(src.isAllDay, round.isAllDay)
        assertEquals(src.startTime, round.startTime)
        assertEquals(src.endTime, round.endTime)
        assertEquals(src.color, round.color)
        assertEquals(src.reminder, round.reminder)
        assertEquals(src.note, round.note)
        assertEquals(src.category, round.category)
        assertEquals(src.linkedTaskId, round.linkedTaskId)
        assertEquals(src.createdAt, round.createdAt)
    }
}