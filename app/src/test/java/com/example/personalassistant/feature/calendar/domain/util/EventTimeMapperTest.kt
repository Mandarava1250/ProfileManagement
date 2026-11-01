package com.example.personalassistant.feature.calendar.domain.util

import com.example.personalassistant.feature.calendar.domain.model.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Phase 8 Sub-step 9 Step 4：EventTimeMapper 单元测试
 *
 * 覆盖：
 * - 整天事件（isAllDay=true / null startTime）→ return null
 * - 普通时段（09:00–10:00）→ 正确切片
 * - 分钟边界（00:00 / 23:59）
 * - 时区用 systemDefault
 */
class EventTimeMapperTest {

    private val DAY = 24L * 60 * 60 * 1000

    private fun event(
        isAllDay: Boolean = false,
        startHour: Int = 9,
        startMin: Int = 0,
        endHour: Int = 10,
        endMin: Int = 0,
        dateOffset: Int = 0,
    ): Event {
        val zone = ZoneId.systemDefault()
        // 以 systemDefault 时区 2026-09-07 周一为锚（避免测试对真实系统时间敏感）
        val anchor = LocalDateTime.of(2026, 9, 7, 0, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val date = anchor + dateOffset * DAY
        val start = date + startHour * 3600_000L + startMin * 60_000L
        val end = date + endHour * 3600_000L + endMin * 60_000L
        return Event(
            id = 1L,
            title = "test",
            date = date,
            isAllDay = isAllDay,
            startTime = if (isAllDay) null else start,
            endTime = if (isAllDay) null else end,
        )
    }

    // ===== 整天事件 = null =====

    @Test
    fun `isAllDay flag true returns null`() {
        val result = EventTimeMapper.toSlice(event(isAllDay = true))
        assertNull(result)
    }

    @Test
    fun `both startTime and endTime null returns null`() {
        val e = Event(
            id = 1L,
            title = "test",
            date = 1_700_000_000_000L,
            isAllDay = false,
            startTime = null,
            endTime = null,
        )
        assertNull(EventTimeMapper.toSlice(e))
    }

    @Test
    fun `only startTime null returns null defensive`() {
        val e = Event(
            id = 1L,
            title = "test",
            date = 1_700_000_000_000L,
            isAllDay = false,
            startTime = null,
            endTime = 100L,
        )
        assertNull(EventTimeMapper.toSlice(e))
    }

    // ===== 普通时段 =====

    @Test
    fun `Monday 09 00 to 10 00 maps to dow=1 startMin=540 endMin=600`() {
        // 2026-09-07 是周一
        val slice = EventTimeMapper.toSlice(event(startHour = 9, endHour = 10))
        assertNotNull(slice)
        assertEquals(1, slice!!.dayOfWeek) // 周一
        assertEquals(540, slice.startMinutes) // 9*60
        assertEquals(600, slice.endMinutes) // 10*60
    }

    @Test
    fun `Sunday is dow=7`() {
        // 2026-09-13 是周日（锚 9/7 周一 + 6 天）
        val slice = EventTimeMapper.toSlice(event(dateOffset = 6, startHour = 14, endHour = 15))
        assertNotNull(slice)
        assertEquals(7, slice!!.dayOfWeek)
        assertEquals(14 * 60, slice.startMinutes)
        assertEquals(15 * 60, slice.endMinutes)
    }

    // ===== 分钟边界 =====

    @Test
    fun `00 00 to 00 01 maps to startMin=0 endMin=1`() {
        val slice = EventTimeMapper.toSlice(event(startHour = 0, startMin = 0, endHour = 0, endMin = 1))
        assertNotNull(slice)
        assertEquals(0, slice!!.startMinutes)
        assertEquals(1, slice.endMinutes)
    }

    @Test
    fun `23 58 to 23 59 maps to startMin=1438 endMin=1439`() {
        val slice = EventTimeMapper.toSlice(event(startHour = 23, startMin = 58, endHour = 23, endMin = 59))
        assertNotNull(slice)
        assertEquals(23 * 60 + 58, slice!!.startMinutes)
        assertEquals(23 * 60 + 59, slice.endMinutes)
    }
}