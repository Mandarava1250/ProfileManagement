package com.example.personalassistant.feature.finance.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批次 D：零钱池「当日净结余」纯计算的 JVM 单测
 *
 * 口径（伙伴已拍板）：
 * ```
 * settleAmount(D) = daily_pools[D].dailyBudget
 *                 + daily_pools[D].totalIncomeToday
 *                 - daily_pools[D].totalSpentToday
 * ```
 *
 * 纯函数 → 不需要 Robolectric，也不需要数据库，毫秒级验证。
 */
class CoinPoolSettlementCalculatorTest {

    @Test
    fun `surplus when spent less than budget`() {
        val amount = CoinPoolSettlementCalculator.settleAmount(
            dailyBudget = 100.0,
            totalIncomeToday = 0.0,
            totalSpentToday = 30.0,
        )
        assertEquals(70.0, amount, 0.0001)
    }

    @Test
    fun `negative when spent more than budget`() {
        val amount = CoinPoolSettlementCalculator.settleAmount(
            dailyBudget = 100.0,
            totalIncomeToday = 0.0,
            totalSpentToday = 150.0,
        )
        assertEquals(-50.0, amount, 0.0001)
    }

    @Test
    fun `zero when spent exactly budget`() {
        val amount = CoinPoolSettlementCalculator.settleAmount(
            dailyBudget = 100.0,
            totalIncomeToday = 0.0,
            totalSpentToday = 100.0,
        )
        assertEquals(0.0, amount, 0.0001)
    }

    @Test
    fun `income increases the settlement amount`() {
        val amount = CoinPoolSettlementCalculator.settleAmount(
            dailyBudget = 100.0,
            totalIncomeToday = 50.0,
            totalSpentToday = 20.0,
        )
        assertEquals(130.0, amount, 0.0001)
    }

    @Test
    fun `income can offset overspend`() {
        // 额度 100，花了 120，但当天又赚了 50 → 净结余 +30（不钳到 0）
        val amount = CoinPoolSettlementCalculator.settleAmount(
            dailyBudget = 100.0,
            totalIncomeToday = 50.0,
            totalSpentToday = 120.0,
        )
        assertEquals(30.0, amount, 0.0001)
    }

    @Test
    fun `all zeros yields zero`() {
        val amount = CoinPoolSettlementCalculator.settleAmount(
            dailyBudget = 0.0,
            totalIncomeToday = 0.0,
            totalSpentToday = 0.0,
        )
        assertEquals(0.0, amount, 0.0001)
    }

    @Test
    fun `no budget but spent money yields negative`() {
        // 兜底建的 daily_pools 行（额度 0）也可能是负的 —— 那天花了钱
        val amount = CoinPoolSettlementCalculator.settleAmount(
            dailyBudget = 0.0,
            totalIncomeToday = 0.0,
            totalSpentToday = 42.0,
        )
        assertEquals(-42.0, amount, 0.0001)
    }

    @Test
    fun `settling twice does not change the per-day amount`() {
        // 结算额是「当天那一行」的纯函数 —— 算两次结果一样，
        // 幂等由「同一天不重复累加」保证，而不是靠这个函数。
        val first = CoinPoolSettlementCalculator.settleAmount(100.0, 0.0, 30.0)
        val second = CoinPoolSettlementCalculator.settleAmount(100.0, 0.0, 30.0)
        assertEquals(first, second, 0.0001)
    }
}
