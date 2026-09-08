package com.example.personalassistant.feature.calendar.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 8 Sub-step 9 Step 4：WeekCalculator 单元测试
 *
 * 覆盖：
 * - 学期起点 / 中段 / 跨周边界
 * - endDate 边界修复（晚于 endDate → null，不再误算很大周次）
 * - startDate 边界（早于 startDate → null）
 * - 起始日（startDate == date → week 1）
 * - 起始日次日（startDate + 1 → week 1）
 */
class WeekCalculatorTest {

    private val DAY = WeekCalculator.ONE_DAY_MILLIS

    // ===== 边界 =====

    @Test
    fun `date exactly equals startDate returns week 1`() {
        val start = 1_700_000_000_000L
        val end = start + 7L * 7 * DAY  // 7 周
        assertEquals(1, WeekCalculator.weekNumber(start, start, end))
    }

    @Test
    fun `date one day before startDate returns null`() {
        val start = 1_700_000_000_000L
        val end = start + 7L * 7 * DAY
        assertNull(WeekCalculator.weekNumber(start - DAY, start, end))
    }

    @Test
    fun `date exactly equals endDate returns last week`() {
        // 7 周学期：第 7 周覆盖 start..start+6W*7D-1，但 endDate == start+6W*7D = start+48.5D 边界
        // 算法 diffDays/7+1 = (endDate-start)/7 +1 = 6*7/7 + 1 = 7
        val start = 1_700_000_000_000L
        val end = start + 6L * 7 * DAY  // 6*7=42 天后
        assertEquals(7, WeekCalculator.weekNumber(end, start, end))
    }

    @Test
    fun `date one day after endDate returns null - endDate boundary bug fix`() {
        val start = 1_700_000_000_000L
        val end = start + 6L * 7 * DAY
        // 旧实现：diffDays/7 + 1 = 7 + 1 = 8（错误）
        // 新实现：null（正确）
        assertNull(WeekCalculator.weekNumber(end + DAY, start, end))
    }

    @Test
    fun `date far after endDate returns null not some large number`() {
        val start = 1_700_000_000_000L
        val end = start + 6L * 7 * DAY
        // 一年后
        assertNull(WeekCalculator.weekNumber(end + 365 * DAY, start, end))
    }

    // ===== 正常值 =====

    @Test
    fun `week 1 spans start to start + 6 days`() {
        val start = 1_700_000_000_000L
        val end = start + 7L * 7 * DAY
        // 起始当天
        assertEquals(1, WeekCalculator.weekNumber(start, start, end))
        // 第 7 天（差 6 天，diffDays=6，week=6/7+1=1）
        assertEquals(1, WeekCalculator.weekNumber(start + 6 * DAY, start, end))
    }

    @Test
    fun `week 2 starts at start + 7 days`() {
        val start = 1_700_000_000_000L
        val end = start + 7L * 7 * DAY
        // 第 8 天（差 7 天）
        assertEquals(2, WeekCalculator.weekNumber(start + 7 * DAY, start, end))
    }

    @Test
    fun `week 3 spans start + 14 to start + 20 days`() {
        val start = 1_700_000_000_000L
        val end = start + 7L * 7 * DAY
        // 第 15 天（差 14 天，week=14/7+1=3）
        assertEquals(3, WeekCalculator.weekNumber(start + 14 * DAY, start, end))
        // 第 21 天（差 20 天，week=20/7+1=3）
        assertEquals(3, WeekCalculator.weekNumber(start + 20 * DAY, start, end))
    }

    @Test
    fun `typical 18-week semester`() {
        val start = 1_700_000_000_000L
        // 18 周覆盖 start..start+17*7+6 = start+125 DAY（周日）
        // endDate 通常 = start + 18*7 = start + 126 DAY（=第 19 周第一天）
        val end = start + 18L * 7 * DAY
        // 第 18 周（差 17*7 = 119 天）
        assertEquals(18, WeekCalculator.weekNumber(start + 17 * 7 * DAY, start, end))
        // endDate 当天 = 第 19 周第一天（差 126 天，126/7+1 = 19）
        assertEquals(19, WeekCalculator.weekNumber(end, start, end))
        // endDate + 1 天 → 超出学期 → null
        assertNull(WeekCalculator.weekNumber(end + DAY, start, end))
    }

    @Test
    fun `zero length semester returns null for any non-start date`() {
        val start = 1_700_000_000_000L
        val end = start  // 同一天 = 学期只有 1 天
        assertEquals(1, WeekCalculator.weekNumber(start, start, end))
        assertNull(WeekCalculator.weekNumber(start + DAY, start, end))
    }
}