package com.example.personalassistant.feature.calendar.domain.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Phase 8 Sub-step 9 Step 3：RecurrenceExpander 单元测试
 *
 * 覆盖：
 * - DAILY 步进
 * - WEEKLY × BYDAY（核心场景）
 * - MONTHLY × BYMONTHDAY（含负数 = 月末）
 * - YEARLY × BYMONTH + BYDAY + BYSETPOS
 * - COUNT / UNTIL 截断
 * - first occurrence 命中规则
 * - EventOccurrence 标记
 */
class RecurrenceExpanderTest {

    // ===== DAILY =====

    @Test
    fun `daily every day`() {
        val rule = RecurrenceRule.daily()
        val start = LocalDate.of(2026, 9, 1)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 9, 7))
        // 9/1..9/7 共 7 天
        assertEquals(7, result.size)
        assertEquals(LocalDate.of(2026, 9, 1), result.first())
        assertEquals(LocalDate.of(2026, 9, 7), result.last())
    }

    @Test
    fun `daily interval 2 every other day`() {
        val rule = RecurrenceRule.daily(interval = 2)
        val start = LocalDate.of(2026, 9, 1)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 9, 10))
        // 9/1, 9/3, 9/5, 9/7, 9/9 共 5 天
        assertEquals(5, result.size)
        assertEquals(listOf(1, 3, 5, 7, 9).map { LocalDate.of(2026, 9, it) }, result)
    }

    // ===== WEEKLY =====

    @Test
    fun `weekly MO and WE from 2026-09-07 (Mon)`() {
        val rule = RecurrenceRule.weekly(days = setOf(WeekDay.MONDAY, WeekDay.WEDNESDAY))
        val start = LocalDate.of(2026, 9, 7)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 9, 21))
        // 9/7 Mon, 9/9 Wed, 9/14 Mon, 9/16 Wed, 9/21 Mon 共 5 天
        assertEquals(5, result.size)
        assertEquals(LocalDate.of(2026, 9, 7), result[0])
        assertEquals(LocalDate.of(2026, 9, 9), result[1])
        assertEquals(LocalDate.of(2026, 9, 14), result[2])
        assertEquals(LocalDate.of(2026, 9, 16), result[3])
        assertEquals(LocalDate.of(2026, 9, 21), result[4])
    }

    @Test
    fun `weekly without BYDAY jumps one week per interval`() {
        val rule = RecurrenceRule.weekly()
        val start = LocalDate.of(2026, 9, 7)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 9, 28))
        // 每周同一天：9/7, 9/14, 9/21, 9/28 共 4 周
        assertEquals(4, result.size)
    }

    @Test
    fun `weekly interval 2 biweekly - same day every 2 weeks`() {
        // Pure WEEKLY interval=2 (no BYDAY): every other Friday
        val rule = RecurrenceRule.weekly(interval = 2)
        val start = LocalDate.of(2026, 9, 4) // Friday
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 10, 16))
        // 9/4, 9/18, 10/2, 10/16 共 4 次
        assertEquals(4, result.size)
        assertEquals(LocalDate.of(2026, 9, 4), result[0])
        assertEquals(LocalDate.of(2026, 10, 16), result[3])
    }

    // KNOWN LIMITATION: WEEKLY interval>1 + BYDAY has not been fully implemented yet.
    // Currently returns every matching weekday in the range, ignoring interval.
    // Full RFC 5545 would emit weekdays only in selected weeks (week 0, interval, 2*interval, ...).
    // TODO: implement week-selection logic for interval-aware BYDAY.

    // ===== MONTHLY =====

    @Test
    fun `monthly day 15 each month`() {
        val rule = RecurrenceRule.monthly(byMonthDay = setOf(15))
        val start = LocalDate.of(2026, 1, 15)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 4, 30))
        // 1/15, 2/15, 3/15, 4/15 共 4 月
        assertEquals(4, result.size)
        assertTrue(result.all { it.dayOfMonth == 15 })
    }

    @Test
    fun `monthly last day of month using BYMONTHDAY -1`() {
        val rule = RecurrenceRule.monthly(byMonthDay = setOf(-1))
        val start = LocalDate.of(2026, 2, 28) // 2 月最后一天
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 5, 31))
        // 2/28, 3/31, 4/30, 5/31 共 4 月
        assertEquals(4, result.size)
        assertEquals(LocalDate.of(2026, 2, 28), result[0])
        assertEquals(LocalDate.of(2026, 3, 31), result[1])
        assertEquals(LocalDate.of(2026, 4, 30), result[2])
        assertEquals(LocalDate.of(2026, 5, 31), result[3])
    }

    @Test
    fun `monthly interval 2 every two months - day 1`() {
        // Pure MONTHLY interval=2 (no BYMONTHDAY): every 2 months on same day as start
        val rule = RecurrenceRule.monthly(interval = 2)
        val start = LocalDate.of(2026, 1, 15)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 7, 31))
        // 1/15, 3/15, 5/15, 7/15 共 4 次
        assertEquals(4, result.size)
    }

    // KNOWN LIMITATION: MONTHLY interval>1 + BYMONTHDAY has not been fully implemented yet.
    // See WEEKLY interval>1 + BYDAY note above.

    // ===== YEARLY =====

    @Test
    fun `yearly October Friday -1 (last Friday of October)`() {
        val rule = RecurrenceRule(
            freq = RecurrenceFreq.YEARLY,
            byMonth = setOf(10),
            byDay = setOf(WeekDay.FRIDAY),
            bySetPos = setOf(-1),
        )
        val start = LocalDate.of(2026, 10, 30) // 2026 年 10 月最后一个周五
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2028, 12, 31))
        // 2026/10/30, 2027/10/29, 2028/10/27 共 3 年
        assertEquals(3, result.size)
        assertEquals(LocalDate.of(2026, 10, 30), result[0])
        assertEquals(LocalDate.of(2027, 10, 29), result[1])
        assertEquals(LocalDate.of(2028, 10, 27), result[2])
    }

    @Test
    fun `yearly January 1 each year`() {
        // "January 1 each year" = YEARLY + byMonth={1} + byMonthDay={1}
        val rule = RecurrenceRule(freq = RecurrenceFreq.YEARLY, byMonth = setOf(1), byMonthDay = setOf(1))
        val start = LocalDate.of(2026, 1, 1)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2028, 12, 31))
        // 2026/1/1, 2027/1/1, 2028/1/1 共 3 年
        assertEquals(3, result.size)
        assertTrue(result.all { it.monthValue == 1 && it.dayOfMonth == 1 })
    }

    // ===== COUNT / UNTIL 截断 =====

    @Test
    fun `count limits occurrences`() {
        val rule = RecurrenceRule(freq = RecurrenceFreq.DAILY, count = 3)
        val start = LocalDate.of(2026, 9, 1)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 12, 31))
        assertEquals(3, result.size)
        assertEquals(LocalDate.of(2026, 9, 3), result.last())
    }

    @Test
    fun `until cuts at end date inclusive`() {
        val rule = RecurrenceRule(
            freq = RecurrenceFreq.DAILY,
            until = LocalDate.of(2026, 9, 5),
        )
        val start = LocalDate.of(2026, 9, 1)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 12, 31))
        // 9/1..9/5 含首尾共 5 天
        assertEquals(5, result.size)
        assertEquals(LocalDate.of(2026, 9, 5), result.last())
    }

    @Test
    fun `start after until returns empty`() {
        val rule = RecurrenceRule(
            freq = RecurrenceFreq.DAILY,
            until = LocalDate.of(2026, 9, 1),
        )
        val start = LocalDate.of(2026, 9, 10) // 已在 until 之后
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 12, 31))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `rangeEnd before start returns empty`() {
        val rule = RecurrenceRule.daily()
        val result = RecurrenceExpander.expand(
            start = LocalDate.of(2026, 9, 10),
            rule = rule,
            rangeEnd = LocalDate.of(2026, 9, 1),
        )
        assertTrue(result.isEmpty())
    }

    // ===== EventOccurrence 标记 =====

    @Test
    fun `expandOccurrences marks first as not recurring`() {
        val rule = RecurrenceRule(freq = RecurrenceFreq.DAILY, count = 3)
        val start = LocalDate.of(2026, 9, 1)
        val result = RecurrenceExpander.expandOccurrences(start, rule, LocalDate.of(2026, 12, 31))
        assertEquals(3, result.size)
        assertEquals(false, result[0].isRecurring)
        assertEquals(true, result[1].isRecurring)
        assertEquals(true, result[2].isRecurring)
    }

    @Test
    fun `expandOccurrences preserves date order`() {
        val rule = RecurrenceRule.weekly(days = setOf(WeekDay.MONDAY))
        val start = LocalDate.of(2026, 9, 7)
        val result = RecurrenceExpander.expandOccurrences(start, rule, LocalDate.of(2026, 10, 31))
        // 9/7, 9/14, 9/21, 9/28, 10/5, 10/12, 10/19, 10/26 共 8 周
        assertEquals(8, result.size)
        assertEquals(LocalDate.of(2026, 9, 7), result.first().date)
        assertEquals(LocalDate.of(2026, 10, 26), result.last().date)
    }

    // ===== 边界 =====

    @Test
    fun `first occurrence is start if matches BYDAY`() {
        // start = 周一，BYDAY = 周一 → first = start
        val rule = RecurrenceRule.weekly(days = setOf(WeekDay.MONDAY))
        val start = LocalDate.of(2026, 9, 7) // 周一
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 9, 14))
        assertEquals(LocalDate.of(2026, 9, 7), result.first())
    }

    @Test
    fun `first occurrence skips start if start weekday does not match BYDAY`() {
        // start = 周一，BYDAY = 周三 → first 应跳到最近的周三
        val rule = RecurrenceRule.weekly(days = setOf(WeekDay.WEDNESDAY))
        val start = LocalDate.of(2026, 9, 7) // 周一
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2026, 9, 14))
        assertEquals(LocalDate.of(2026, 9, 9), result.first())
    }

    @Test
    fun `large range does not exceed MAX_OCCURRENCES safety limit`() {
        val rule = RecurrenceRule.daily()
        val start = LocalDate.of(2026, 1, 1)
        val result = RecurrenceExpander.expand(start, rule, LocalDate.of(2027, 1, 1))
        // 安全上限 366，应该不会无限增长
        assertTrue(result.size <= 366)
    }
}