package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

/**
 * Phase 7a：SetMonthlyBudgetUseCase 单元测试
 *
 * 覆盖：
 * - totalAmount < 0 被拒绝
 * - yearMonth 格式不对被拒绝
 * - 正常路径委托 Repository.setMonthlyBudget，返回 id
 * - 同一 month 二次设置覆盖（不新增第二条）
 * - observeCurrentMonthBudget 反映最新值
 */
class SetMonthlyBudgetUseCaseTest {

    private lateinit var repo: FakeFinanceRepository
    private lateinit var useCase: SetMonthlyBudgetUseCase

    @Before
    fun setup() {
        repo = FakeFinanceRepository()
        useCase = SetMonthlyBudgetUseCase(repo)
    }

    @Test
    fun `negative total amount is rejected`() = runTest {
        try {
            useCase(yearMonth = "2026-09", totalAmount = -1.0)
            fail("Expected IllegalArgumentException for negative amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("预算"))
        }
    }

    @Test
    fun `zero total amount is accepted`() = runTest {
        val id = useCase(yearMonth = "2026-09", totalAmount = 0.0)
        assertTrue(id > 0L)
        val budgets = repo.observeAllBudgets().first()
        assertEquals(1, budgets.size)
        assertEquals(0.0, budgets[0].totalAmount, 0.0)
    }

    @Test
    fun `malformed yearMonth is rejected`() = runTest {
        listOf("", "2026", "2026/09", "26-09", "2026-9").forEach { bad ->
            try {
                useCase(yearMonth = bad, totalAmount = 100.0)
                fail("Expected IllegalArgumentException for yearMonth='$bad'")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("yyyy-MM"))
            }
        }
    }

    @Test
    fun `valid call persists budget`() = runTest {
        val id = useCase(
            yearMonth = "2026-09",
            totalAmount = 1500.0,
            isAutoRefresh = true,
            carryOverEnabled = false,
            warningThreshold = 100.0,
        )
        assertTrue(id > 0L)
        val budgets = repo.observeAllBudgets().first()
        assertEquals(1, budgets.size)
        val b = budgets[0]
        assertEquals("2026-09", b.yearMonth)
        assertEquals(1500.0, b.totalAmount, 0.0)
        assertEquals(false, b.carryOverEnabled)
        assertEquals(100.0, b.warningThreshold, 0.0)
    }

    @Test
    fun `setting same month again updates instead of insert`() = runTest {
        val id1 = useCase(yearMonth = "2026-09", totalAmount = 1500.0)
        val id2 = useCase(yearMonth = "2026-09", totalAmount = 2000.0)
        assertEquals(id1, id2)
        val budgets = repo.observeAllBudgets().first()
        assertEquals(1, budgets.size)
        assertEquals(2000.0, budgets[0].totalAmount, 0.0)
    }

    @Test
    fun `observeCurrentMonthBudget reflects the current month only`() = runTest {
        // ⚠️ 当月必须**动态算**，不能写死。
        //
        // 原来这里写的是 "2026-08" / "2026-09" 两个固定月份，而
        // observeCurrentMonthBudget() 查的是仓库自己的 currentYearMonth()（= 墙上时钟的当月）。
        // 写这枚测试的时候机器恰好在 8/9 月，所以它是绿的 —— 一枚定时炸弹。
        // 跨到 2026-10 时查 "2026-10" 查不到记录 → 返回 null → assertNotNull 假红。
        // 这枚测试红了几个月，根子在这里，不在业务代码。
        val currentYm = YearMonth.now(ZoneId.systemDefault()).toString()
        val previousYm = YearMonth.now(ZoneId.systemDefault()).minusMonths(1).toString()

        useCase(yearMonth = previousYm, totalAmount = 100.0)
        useCase(yearMonth = currentYm, totalAmount = 1500.0)

        val current = repo.observeCurrentMonthBudget().first()
        assertNotNull("当月 $currentYm 应当有预算", current)
        // 只能返回当月那一份：上个月那笔 100.0 不该被捞出来
        assertEquals(currentYm, current!!.yearMonth)
        assertEquals(1500.0, current.totalAmount, 0.0)
    }
}
