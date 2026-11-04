package com.example.personalassistant.feature.finance.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 批次 D：零钱池日切结算（含负数）的 JVM 单测
 *
 * 覆盖：
 * - 没有 `daily_pools` 行 → 不产生结转（余额仍为 0，但水位线推进）
 * - 有结余 → 累加进余额
 * - 超支 → 结算额为负，余额被拉低（甚至变负）
 * - 同一天重复结算 → **不重复累加**（幂等）
 */
class CoinPoolRepositoryTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `no pool initially`() = runTest {
        val repo = FakeFinanceRepository()
        assertNull(repo.observeCoinPool().first())
    }

    @Test
    fun `settle without daily pool row settles nothing`() = runTest {
        val repo = FakeFinanceRepository()
        val day = LocalDate.of(2026, 10, 5)
        val result = repo.settleCoinPool(day)

        assertEquals(0, result.settledDays)
        assertEquals(0.0, result.settledAmount, 0.001)
        // 那天没参与预算执行 → **没有结转额**（余额不变）。
        // 但水位线仍要推进 —— 幂等靠它，而水位线记在零钱池行上，
        // 所以会建一个余额为 0 的空行。
        val pool = requireNotNull(repo.observeCoinPool().first())
        assertEquals(0.0, pool.balance, 0.001)
        assertEquals(startOfDay(day), pool.lastSettledDate)
    }

    @Test
    fun `settle adds daily surplus to balance`() = runTest {
        val repo = FakeFinanceRepository()
        val day = LocalDate.of(2026, 10, 5)
        // 当日额度 100，花了 30 → 净结余 70
        repo.seedDailyPool(date = startOfDay(day), dailyBudget = 100.0, totalSpentToday = 30.0)

        val result = repo.settleCoinPool(day)

        assertEquals(1, result.settledDays)
        assertEquals(70.0, result.settledAmount, 0.001)
        assertEquals(70.0, result.balance, 0.001)
        val pool = repo.observeCoinPool().first()
        assertNotNull(pool)
        assertEquals(70.0, pool!!.balance, 0.001)
        assertEquals(startOfDay(day), pool.lastSettledDate)
        assertTrue(pool.hasSettled)
    }

    @Test
    fun `settle subtracts overspend making balance negative`() = runTest {
        val repo = FakeFinanceRepository()
        val day = LocalDate.of(2026, 10, 5)
        // 当日额度 100，花了 150 → 净结余 -50
        repo.seedDailyPool(date = startOfDay(day), dailyBudget = 100.0, totalSpentToday = 150.0)

        val result = repo.settleCoinPool(day)

        assertEquals(-50.0, result.settledAmount, 0.001)
        assertEquals(-50.0, result.balance, 0.001)
        assertTrue(repo.observeCoinPool().first()!!.isNegative)
    }

    @Test
    fun `settle accumulates across days`() = runTest {
        val repo = FakeFinanceRepository()
        val d1 = LocalDate.of(2026, 10, 5)
        val d2 = LocalDate.of(2026, 10, 6)
        repo.seedDailyPool(date = startOfDay(d1), dailyBudget = 100.0, totalSpentToday = 30.0)
        repo.seedDailyPool(date = startOfDay(d2), dailyBudget = 100.0, totalSpentToday = 80.0)

        repo.settleCoinPool(d1)
        val result = repo.settleCoinPool(d2)

        // 70 + 20 = 90
        assertEquals(90.0, result.balance, 0.001)
    }

    @Test
    fun `settle same day twice does not double count`() = runTest {
        val repo = FakeFinanceRepository()
        val day = LocalDate.of(2026, 10, 5)
        repo.seedDailyPool(date = startOfDay(day), dailyBudget = 100.0, totalSpentToday = 30.0)

        repo.settleCoinPool(day)
        val second = repo.settleCoinPool(day)

        // 幂等：不会变成 140
        assertEquals(0, second.settledDays)
        assertEquals(0.0, second.settledAmount, 0.001)
        assertEquals(70.0, second.balance, 0.001)
    }

    @Test
    fun `settle counts income into daily surplus`() = runTest {
        val repo = FakeFinanceRepository()
        val day = LocalDate.of(2026, 10, 5)
        // 额度 100 + 收入 50 − 支出 20 = 130
        repo.seedDailyPool(
            date = startOfDay(day),
            dailyBudget = 100.0,
            totalIncomeToday = 50.0,
            totalSpentToday = 20.0,
        )

        val result = repo.settleCoinPool(day)

        assertEquals(130.0, result.settledAmount, 0.001)
    }
}
