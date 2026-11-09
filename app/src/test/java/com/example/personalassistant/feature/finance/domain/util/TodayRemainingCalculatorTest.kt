package com.example.personalassistant.feature.finance.domain.util

import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 批次 C：「今日剩余」纯计算的 JVM 单测
 *
 * 覆盖：
 * - FIXED / AUTO 两种口径的公式
 * - 边界：未设预算（null）/ 已超支（负数）/ 剩余 1 天 / 剩余天数含今天
 */
class TodayRemainingCalculatorTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    // ==================== FIXED ====================

    @Test
    fun `FIXED returns month remaining`() {
        val result = TodayRemainingCalculator.compute(
            mode = TodayRemainingMode.FIXED,
            monthBudgetTotal = 3000.0,
            monthExpense = 1000.0,
            remainingDays = 10,
        )
        assertEquals(2000.0, result!!, 0.001)
    }

    @Test
    fun `FIXED ignores remainingDays`() {
        val a = TodayRemainingCalculator.compute(TodayRemainingMode.FIXED, 3000.0, 1000.0, 10)
        val b = TodayRemainingCalculator.compute(TodayRemainingMode.FIXED, 3000.0, 1000.0, 1)
        assertEquals(a!!, b!!, 0.001)
    }

    // ==================== AUTO ====================

    @Test
    fun `AUTO divides by remaining days`() {
        val result = TodayRemainingCalculator.compute(
            mode = TodayRemainingMode.AUTO,
            monthBudgetTotal = 3000.0,
            monthExpense = 1000.0,
            remainingDays = 10,
        )
        assertEquals(200.0, result!!, 0.001)
    }

    @Test
    fun `AUTO with one remaining day equals FIXED`() {
        val auto = TodayRemainingCalculator.compute(TodayRemainingMode.AUTO, 3000.0, 1000.0, 1)
        val fixed = TodayRemainingCalculator.compute(TodayRemainingMode.FIXED, 3000.0, 1000.0, 1)
        assertEquals(fixed!!, auto!!, 0.001)
    }

    @Test
    fun `AUTO with zero remaining days is treated as one day`() {
        // 剩余天数最少 1 天：不能出现除以 0
        val result = TodayRemainingCalculator.compute(
            mode = TodayRemainingMode.AUTO,
            monthBudgetTotal = 3000.0,
            monthExpense = 1000.0,
            remainingDays = 0,
        )
        assertEquals(2000.0, result!!, 0.001)
    }

    @Test
    fun `AUTO with negative remaining days is treated as one day`() {
        val result = TodayRemainingCalculator.compute(
            mode = TodayRemainingMode.AUTO,
            monthBudgetTotal = 3000.0,
            monthExpense = 1000.0,
            remainingDays = -5,
        )
        assertEquals(2000.0, result!!, 0.001)
    }

    // ==================== 边界 ====================

    @Test
    fun `null budget returns null in FIXED`() {
        assertNull(
            TodayRemainingCalculator.compute(TodayRemainingMode.FIXED, null, 1000.0, 10)
        )
    }

    @Test
    fun `null budget returns null in AUTO`() {
        assertNull(
            TodayRemainingCalculator.compute(TodayRemainingMode.AUTO, null, 1000.0, 10)
        )
    }

    @Test
    fun `null expense is treated as zero`() {
        val result = TodayRemainingCalculator.compute(TodayRemainingMode.FIXED, 3000.0, null, 10)
        assertEquals(3000.0, result!!, 0.001)
    }

    @Test
    fun `FIXED overspend returns negative`() {
        val result = TodayRemainingCalculator.compute(TodayRemainingMode.FIXED, 3000.0, 5000.0, 10)
        assertEquals(-2000.0, result!!, 0.001)
    }

    @Test
    fun `AUTO overspend returns negative`() {
        // 超支时**不钳到 0** —— 用户要看出「超了多少」
        val result = TodayRemainingCalculator.compute(TodayRemainingMode.AUTO, 3000.0, 5000.0, 10)
        assertEquals(-200.0, result!!, 0.001)
    }

    // ==================== 剩余天数含今天（账期口径）===================

    /**
     * 账期剩余天数是**账期**口径，不是自然月口径。
     *
     * 截止日 = 1 时，8 月账期 = `[08-01, 09-01)`（左闭右开，连续切分）。
     * 8/31 是这个账期的**最后一天** —— 9/1 已经属于 9 月账期了。
     * 所以「含今天」= 只剩今天这一天 = **1 天**。
     *
     * ⚠️ 别写成 2 天：那是把 9/1 也算进 8 月账期了，
     * 与 [BillingCycle] 「上一段的终点 = 下一段的起点」的切分规则冲突。
     * [BillingCycle] 自己的 KDoc 也写明「截止日 = 1 号时等价于
     * `daysInMonth - dayOfMonth + 1`」= 31 − 31 + 1 = 1。
     */
    @Test
    fun `remaining days includes today`() {
        // 截止日 = 1（自然月）：8 月 31 日是 8 月账期的最后一天 → 只剩今天 1 天
        val date = LocalDate.of(2026, 8, 31)
        val days = BillingCycle.remainingDays(date, MonthCutoffPolicy(), zone)
        assertEquals(1, days)
    }

    @Test
    fun `remaining days at least one on the last day of cycle`() {
        // 截止日 = 1：8 月 30 日 → 8/30 + 8/31 = 2 天
        val date = LocalDate.of(2026, 8, 30)
        val days = BillingCycle.remainingDays(date, MonthCutoffPolicy(), zone)
        assertEquals(2, days)
    }

    /**
     * 截止日 ≠ 1 时，账期天数与自然月天数**完全不同** —— 这组对照用来钉死口径。
     *
     * 截止日 = 15 时，8 月账期 = `[08-15, 09-15)`：
     * - 8/14 属于 7 月账期（`[07-15, 08-15)`）→ 只剩 1 天
     *   （自然月口径会算成 8 月还剩 17 天 —— 差 16 天）
     * - 8/31 属于 8 月账期 → 还剩 15 天
     *   （自然月口径会算成只剩 1 天 —— 差 14 天）
     *
     * 「今日剩余」按自然月摊销会在月初摊得太多、月末摊得太少，
     * 所以这里必须走账期天数。
     */
    @Test
    fun `cycle remaining days differ from natural month days`() {
        val policy = MonthCutoffPolicy(cutoffDay = 15, effectiveFromYm = "1970-01")

        // 8/14 落在 7 月账期，账期只剩今天 1 天（自然月还剩 17 天）
        assertEquals(
            1,
            BillingCycle.remainingDays(LocalDate.of(2026, 8, 14), policy, zone),
        )
        // 8/31 落在 8 月账期，账期还剩 8/31 ~ 9/14 共 15 天（自然月只剩 1 天）
        assertEquals(
            15,
            BillingCycle.remainingDays(LocalDate.of(2026, 8, 31), policy, zone),
        )
    }

    @Test
    fun `compute with date and policy uses cycle remaining days`() {
        // 2026-08-31：截止日 = 1 时，8 月账期只剩今天 → 1 天
        val result = TodayRemainingCalculator.compute(
            mode = TodayRemainingMode.AUTO,
            monthBudgetTotal = 3000.0,
            monthExpense = 1000.0,
            today = LocalDate.of(2026, 8, 31),
            policy = MonthCutoffPolicy(),
            zone = zone,
        )
        // 2000 / 1 = 2000（不是 / 2 —— 9/1 不属于 8 月账期）
        assertEquals(2000.0, result!!, 0.001)
    }

    /**
     * AUTO 口径按**账期**天数摊销，不是自然月天数。
     *
     * 截止日 = 15 时 8/31 的账期还剩 15 天 → 2000 / 15。
     * 若实现误用自然月天数（只剩 1 天），这里会得到 2000 而不是 133.33。
     */
    @Test
    fun `AUTO spreads over cycle days not natural month days`() {
        val result = TodayRemainingCalculator.compute(
            mode = TodayRemainingMode.AUTO,
            monthBudgetTotal = 3000.0,
            monthExpense = 1000.0,
            today = LocalDate.of(2026, 8, 31),
            policy = MonthCutoffPolicy(cutoffDay = 15, effectiveFromYm = "1970-01"),
            zone = zone,
        )
        assertEquals(2000.0 / 15.0, result!!, 0.0001)
    }

    // ==================== monthRemaining ====================

    @Test
    fun `monthRemaining null budget returns null`() {
        assertNull(TodayRemainingCalculator.monthRemaining(null, 100.0))
    }

    @Test
    fun `monthRemaining is budget minus expense`() {
        assertEquals(2000.0, TodayRemainingCalculator.monthRemaining(3000.0, 1000.0)!!, 0.001)
    }
}
