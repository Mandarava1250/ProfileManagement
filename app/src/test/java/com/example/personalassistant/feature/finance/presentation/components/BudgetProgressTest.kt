package com.example.personalassistant.feature.finance.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9b D2：`BudgetProgress` 的 JVM 单测。
 *
 * 预算执行条的推导（比例 / 百分比 / 剩余 / 超支判定 / 未设预算）全在这里覆盖 ——
 * instrumented 侧只验渲染与点击展开，不重复验算。
 */
class BudgetProgressTest {

    // ===== 比例与百分比 =====

    /** 花掉一半 → 50% */
    @Test
    fun `half spent yields fifty percent`() {
        val p = BudgetProgress(budgetAmount = 1000.0, spentAmount = 500.0)

        assertEquals(0.5, p.ratio, 0.0001)
        assertEquals("50%", p.percentText)
        assertEquals(0.5f, p.clampedRatio, 0.0001f)
    }

    /** 一分没花 → 0% */
    @Test
    fun `nothing spent yields zero percent`() {
        val p = BudgetProgress(budgetAmount = 1000.0, spentAmount = 0.0)

        assertEquals(0.0, p.ratio, 0.0001)
        assertEquals("0%", p.percentText)
        assertEquals(0.0f, p.clampedRatio, 0.0001f)
    }

    /** 花光 → 100%，且不算超支 */
    @Test
    fun `fully spent is exactly one hundred percent and not over`() {
        val p = BudgetProgress(budgetAmount = 1000.0, spentAmount = 1000.0)

        assertEquals(1.0, p.ratio, 0.0001)
        assertEquals("100%", p.percentText)
        assertFalse(p.isOverBudget)
        assertEquals(0.0, p.remaining, 0.0001)
    }

    /** 超支 → 比例 > 1，百分比 > 100%，进度条钳制到 1 */
    @Test
    fun `over budget exceeds ratio but clamps bar`() {
        val p = BudgetProgress(budgetAmount = 1000.0, spentAmount = 1500.0)

        assertEquals(1.5, p.ratio, 0.0001)
        assertEquals("150%", p.percentText)
        assertEquals(1.0f, p.clampedRatio, 0.0001f)
        assertTrue(p.isOverBudget)
        assertEquals(-500.0, p.remaining, 0.0001)
    }

    /** 百分比取整（截断而非四舍五入 —— 与既有 MoneyFormat.formatPercent 一致） */
    @Test
    fun `percent truncates to int`() {
        val p = BudgetProgress(budgetAmount = 1000.0, spentAmount = 333.0)

        assertEquals("33%", p.percentText)
    }

    // ===== 预算为 0 / 负数 的除零保护 =====

    /** 预算为 0 → 比例 0、0%（不产生 NaN） */
    @Test
    fun `zero budget yields zero ratio`() {
        val p = BudgetProgress(budgetAmount = 0.0, spentAmount = 100.0)

        assertEquals(0.0, p.ratio, 0.0001)
        assertEquals("0%", p.percentText)
        assertEquals(0.0f, p.clampedRatio, 0.0001f)
    }

    /** 负预算（异常数据）同样按 0 处理，不崩 */
    @Test
    fun `negative budget yields zero ratio`() {
        val p = BudgetProgress(budgetAmount = -100.0, spentAmount = 50.0)

        assertEquals(0.0, p.ratio, 0.0001)
        assertEquals("0%", p.percentText)
    }

    // ===== 剩余 =====

    /** 未花完 → 剩余为正 */
    @Test
    fun `remaining positive when under budget`() {
        val p = BudgetProgress(budgetAmount = 1000.0, spentAmount = 300.0)

        assertEquals(700.0, p.remaining, 0.0001)
        assertFalse(p.isOverBudget)
    }

    // ===== fromOrNull：未设预算 =====

    /** budget 为 null（用户未设预算）→ 返回 null，UI 整块省略 */
    @Test
    fun `null budget yields null progress`() {
        assertNull(BudgetProgress.fromOrNull(budgetAmount = null, spentAmount = 100.0))
    }

    /** 有预算 → 正常构造 */
    @Test
    fun `non null budget builds progress`() {
        val p = BudgetProgress.fromOrNull(budgetAmount = 2000.0, spentAmount = 500.0)

        assertEquals(2000.0, p!!.budgetAmount, 0.0001)
        assertEquals(500.0, p.spentAmount, 0.0001)
        assertEquals("25%", p.percentText)
    }

    /** 预算为 0 也算「已设预算」（与 null 是两件事：0 是明确设成 0） */
    @Test
    fun `zero budget is still a set budget`() {
        val p = BudgetProgress.fromOrNull(budgetAmount = 0.0, spentAmount = 10.0)

        assertEquals(0.0, p!!.budgetAmount, 0.0001)
        // 比例按 0 处理（保护），但组件会渲染（因为用户确实设了 0）
        assertEquals("0%", p.percentText)
    }
}