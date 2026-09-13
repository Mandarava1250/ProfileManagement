package com.example.personalassistant.feature.finance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5：跨账户汇总模型的折叠与派生规则。
 *
 * 全部是纯函数，因此能在 JVM 上严格断言 —— 而这些规则恰好是「数字看着对、
 * 结论却是错的」最容易发生的地方：合计少加一个账户、转账被当成支出、
 * 半对转账被平均成一个看起来正常的数。
 */
class AccountSummaryTest {

    private fun row(
        id: Long = 1L,
        budget: Double?,
        expense: Double = 0.0,
        transferIn: Double = 0.0,
        transferOut: Double = 0.0,
        initial: Double = 0.0,
    ) = AccountBudgetSummary(
        accountId = id,
        accountName = "账户$id",
        budgetAmount = budget,
        expense = expense,
        transferIn = transferIn,
        transferOut = transferOut,
        initialBalance = initial,
    )

    // ===== 未设预算 ≠ 预算为 0 =====

    @Test
    fun `未设预算的行不计入合计但仍占一个账户位并被单独计数`() {
        val totals = BudgetSummaryTotals.from(
            listOf(
                row(id = 1L, budget = 1000.0, expense = 300.0),
                row(id = 2L, budget = null, expense = 50.0),
            ),
        )

        // 预算侧：只有设了预算的那个账户进分母
        assertEquals(1000.0, totals.totalBudget, 0.0001)
        // 支出侧：两个账户都进（钱是真花掉的）
        assertEquals(350.0, totals.totalExpense, 0.0001)
        assertEquals(2, totals.accountCount)
        // 关键：缺口必须暴露出来，否则用户不知道 totalBudget 少算了一个账户
        assertEquals(1, totals.accountsMissingBudget)
    }

    @Test
    fun `预算设成 0 与未设预算是两件事`() {
        val zero = row(budget = 0.0)
        val missing = row(budget = null)

        assertFalse("预算 0 是明确设定，不算缺失", zero.budgetMissing)
        assertTrue("null 才是未设", missing.budgetMissing)
        // 预算 0 且支出 0：不超支
        assertFalse(zero.isOverBudget)
        // 未设预算：谈不上超支，绝不能报成「超支」
        assertFalse(missing.isOverBudget)
        assertNull("未设预算时剩余是 null，不能当 0 用", missing.remaining)
    }

    @Test
    fun `全部账户都未设预算时合计为 0 且不除零`() {
        val totals = BudgetSummaryTotals.from(
            listOf(row(id = 1L, budget = null, expense = 80.0), row(id = 2L, budget = null)),
        )
        assertEquals(0.0, totals.totalBudget, 0.0001)
        // usedRatio 分母为 0 —— 模型里必须返回 0 而不是 NaN/异常
        assertEquals(0.0, totals.usedRatio, 0.0001)
        assertEquals(2, totals.accountsMissingBudget)
    }

    @Test
    fun `超支判定严格大于`() {
        assertFalse("等于预算不算超", row(budget = 100.0, expense = 100.0).isOverBudget)
        assertTrue(row(budget = 100.0, expense = 100.01).isOverBudget)
        assertEquals(0.0, row(budget = 100.0, expense = 100.0).remaining ?: -1.0, 0.0001)
    }

    // ===== 转账不污染支出 =====

    @Test
    fun `转账进出单独成项 不并进支出与预算剩余`() {
        val r = row(id = 1L, budget = 1000.0, expense = 200.0, transferIn = 500.0, transferOut = 100.0)
        // 剩余只看 expense；转账改了余额但不该动预算剩余
        assertEquals(800.0, r.remaining ?: -1.0, 0.0001)
        assertEquals(500.0, r.transferIn, 0.0001)
        assertEquals(100.0, r.transferOut, 0.0001)

        val totals = BudgetSummaryTotals.from(listOf(r))
        // 合计的 totalExpense 不受转账影响
        assertEquals(200.0, totals.totalExpense, 0.0001)
        // 转账总额取转入侧
        assertEquals(500.0, totals.totalTransfer, 0.0001)
    }

    // ===== 半对转账是可检测的数据异常 =====

    @Test
    fun `转入转出对不上时报不平衡 而不是偷偷取平均`() {
        val balanced = BudgetSummaryTotals.from(
            listOf(row(id = 1L, budget = 100.0, transferOut = 300.0), row(id = 2L, budget = 100.0, transferIn = 300.0)),
        )
        assertFalse("正常成对转账两边相等", balanced.transferImbalanced)
        assertEquals(300.0, balanced.totalTransfer, 0.0001)

        // 模拟「只导入了一半」：A 有转出、B 没有对应转入
        val broken = BudgetSummaryTotals.from(
            listOf(row(id = 1L, budget = 100.0, transferOut = 300.0), row(id = 2L, budget = 100.0)),
        )
        assertTrue("半对转账必须被标成异常", broken.transferImbalanced)
        // 且不能因为不平衡就把 totalTransfer 变成中间值 —— 它仍如实取转入侧（0）
        assertEquals(0.0, broken.totalTransfer, 0.0001)
    }

    @Test
    fun `一分以内的浮点误差不算不平衡`() {
        // 用 0.1+0.2 这种**确定存在**的二进制浮点误差（结果 0.30000000000000004），
        // 而不是 a+x-x 这类可能恰好抵消的写法 —— 后者会随表达式而变，测不到容差边界。
        val totals = BudgetSummaryTotals.from(
            listOf(
                row(id = 1L, budget = 100.0, transferOut = 0.1 + 0.2),
                row(id = 2L, budget = 100.0, transferIn = 0.3),
            ),
        )
        assertTrue(
            "本用例的前提是两边确实不相等（否则测不到容差）",
            (0.1 + 0.2) != 0.3,
        )
        assertFalse("1 分钱以内的差异应被容差吃拢", totals.transferImbalanced)
    }

    // ===== 派生余额要能自证 =====

    @Test
    fun `余额等于期初加收入减支出加净转入`() {
        val balance = AccountBalance(
            accountId = 1L,
            accountName = "A",
            initialBalance = 500.0,
            income = 200.0,
            expense = 380.0,
            transferNet = -50.0,
        )
        assertEquals(270.0, balance.balance, 0.0001)
    }

    @Test
    fun `净转入为零时余额不受转账影响`() {
        val balance = AccountBalance(1L, "A", initialBalance = 100.0, income = 0.0, expense = 0.0, transferNet = 0.0)
        assertEquals(100.0, balance.balance, 0.0001)
    }

    // ===== 流水侧全局合计 =====

    @Test
    fun `全局结余只由收支决定 转账不改结余`() {
        val sums = GlobalMonthlySums(expense = 400.0, income = 1000.0, transferOut = 300.0, transferIn = 300.0)
        assertEquals(600.0, sums.balance, 0.0001)
        assertFalse(sums.transferImbalanced)
    }

    @Test
    fun `空表时全局合计四项均为零而非空`() {
        val empty = GlobalMonthlySums(0.0, 0.0, 0.0, 0.0)
        assertEquals(0.0, empty.balance, 0.0001)
        assertFalse(empty.transferImbalanced)
    }
}
