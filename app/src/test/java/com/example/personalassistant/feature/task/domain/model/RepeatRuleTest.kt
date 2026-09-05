package com.example.personalassistant.feature.task.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * RepeatRule 各分支的 nextOccurrence 行为测试
 *
 * 重点覆盖：
 * - 基础 5 种规则（None/Daily/Weekly/Monthly/Yearly）的"+1 周期"正确性
 * - CustomWeekdays 在跨周/不跨周情形下的"下一个匹配 weekday"逻辑
 * - 边界：Monthly 落到 2/30 等不存在日期的回退（用月末兜底）
 * - 防御性：CustomWeekdays 拒绝空集合
 * - RepeatType ↔ RepeatRule 互转
 */
class RepeatRuleTest {

    private val baseDate: LocalDate = LocalDate.of(2026, 9, 7) // 2026-09-07 是周一

    // ===== None =====

    @Test
    fun `None returns null`() {
        assertNull(RepeatRule.None.nextOccurrence(baseDate))
    }

    // ===== Daily =====

    @Test
    fun `Daily adds one day`() {
        assertEquals(baseDate.plusDays(1), RepeatRule.Daily.nextOccurrence(baseDate))
    }

    @Test
    fun `Daily crosses month boundary`() {
        val lastDayOfMonth = LocalDate.of(2026, 9, 30)
        assertEquals(LocalDate.of(2026, 10, 1), RepeatRule.Daily.nextOccurrence(lastDayOfMonth))
    }

    // ===== Weekly =====

    @Test
    fun `Weekly adds one week`() {
        assertEquals(baseDate.plusWeeks(1), RepeatRule.Weekly.nextOccurrence(baseDate))
    }

    // ===== Monthly =====

    @Test
    fun `Monthly keeps day of month`() {
        val date = LocalDate.of(2026, 9, 15)
        assertEquals(LocalDate.of(2026, 10, 15), RepeatRule.Monthly.nextOccurrence(date))
    }

    @Test
    fun `Monthly falls back to last day when target month is shorter (Jan 31 to Feb 28)`() {
        val date = LocalDate.of(2026, 1, 31)
        val next = RepeatRule.Monthly.nextOccurrence(date)
        assertEquals(LocalDate.of(2026, 2, 28), next)
    }

    @Test
    fun `Monthly in leap year Jan 31 to Feb 29`() {
        val date = LocalDate.of(2024, 1, 31)
        val next = RepeatRule.Monthly.nextOccurrence(date)
        assertEquals(LocalDate.of(2024, 2, 29), next)
    }

    // ===== Yearly =====

    @Test
    fun `Yearly adds one year`() {
        val date = LocalDate.of(2026, 6, 15)
        assertEquals(LocalDate.of(2027, 6, 15), RepeatRule.Yearly.nextOccurrence(date))
    }

    @Test
    fun `Yearly across leap day (Feb 29 to Feb 28 next year)`() {
        val date = LocalDate.of(2024, 2, 29)
        // LocalDate.plusYears 会丢到 2025-02-28（API 默认行为）
        val next = RepeatRule.Yearly.nextOccurrence(date)
        assertEquals(LocalDate.of(2025, 2, 28), next)
    }

    // ===== CustomWeekdays =====

    @Test
    fun `CustomWeekdays next occurrence same week`() {
        // baseDate = 周一；规则 = 周一/周三/周五
        val rule = RepeatRule.CustomWeekdays(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )
        // 下一个匹配的是 周二之后？周二不在集合里 → 周三
        assertEquals(LocalDate.of(2026, 9, 9), rule.nextOccurrence(baseDate))
    }

    @Test
    fun `CustomWeekdays next occurrence next week when no more this week`() {
        // baseDate = 周五；规则 = 周一/周三
        val friday = LocalDate.of(2026, 9, 11)
        val rule = RepeatRule.CustomWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        // 周六/周日跳过 → 下周一
        assertEquals(LocalDate.of(2026, 9, 14), rule.nextOccurrence(friday))
    }

    @Test
    fun `CustomWeekdays single day only matches that weekday`() {
        // baseDate = 周一；规则只有周五 → 跳到本周五
        val rule = RepeatRule.CustomWeekdays(setOf(DayOfWeek.FRIDAY))
        assertEquals(LocalDate.of(2026, 9, 11), rule.nextOccurrence(baseDate))
    }

    @Test
    fun `CustomWeekdays rejects empty set`() {
        try {
            RepeatRule.CustomWeekdays(emptySet())
            fail("应该抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    // ===== Presets =====

    @Test
    fun `WEEKDAYS preset covers Mon to Fri`() {
        assertEquals(5, RepeatRule.CustomWeekdays.WEEKDAYS.size)
        assertTrue(DayOfWeek.SATURDAY !in RepeatRule.CustomWeekdays.WEEKDAYS)
    }

    @Test
    fun `WEEKEND preset covers Sat and Sun`() {
        assertEquals(2, RepeatRule.CustomWeekdays.WEEKEND.size)
        assertTrue(DayOfWeek.SATURDAY in RepeatRule.CustomWeekdays.WEEKEND)
        assertTrue(DayOfWeek.SUNDAY in RepeatRule.CustomWeekdays.WEEKEND)
    }

    // ===== RepeatType ↔ RepeatRule =====

    @Test
    fun `fromRepeatType maps NONE to None`() {
        assertEquals(RepeatRule.None, RepeatRule.fromRepeatType(RepeatType.NONE))
    }

    @Test
    fun `fromRepeatType maps DAILY to Daily`() {
        assertEquals(RepeatRule.Daily, RepeatRule.fromRepeatType(RepeatType.DAILY))
    }

    @Test
    fun `fromRepeatType with custom days promotes to CustomWeekdays`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        val rule = RepeatRule.fromRepeatType(RepeatType.DAILY, customDays = days)
        assertEquals(RepeatRule.CustomWeekdays(days), rule)
    }

    @Test
    fun `toRepeatType CustomWeekdays degrades to DAILY (Phase 4a)`() {
        val rule = RepeatRule.CustomWeekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val (type, days) = RepeatRule.toRepeatType(rule)
        assertEquals(RepeatType.DAILY, type)
        // days 实际还传出来了，但 DB 不会持久化（Mapper 暂不写）
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), days)
    }

    @Test
    fun `toRepeatType None maps to NONE`() {
        val (type, days) = RepeatRule.toRepeatType(RepeatRule.None)
        assertEquals(RepeatType.NONE, type)
        assertNull(days)
    }
}
