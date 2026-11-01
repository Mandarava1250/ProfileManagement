package com.example.personalassistant.feature.finance.domain.model

/**
 * Phase 7a：月度报表（一次性快照，UI 渲染用）
 *
 * - [budget] 当月预算（可能为 null：用户未设置）
 * - [totalExpense] / [totalIncome] 当月累计支出/收入
 * - [expenseByCategory] 支出按分类汇总 [(categoryId, totalAmount)]，按金额降序
 * - [monthExpenseTrend] 按日支出趋势 [(dayBucketEpochDay, totalAmount)]，按日期升序
 *
 * 提供 [balance] / [usedRatio] 便捷字段给 UI 显示整体节奏。
 */
data class MonthlyReport(
    val yearMonth: String,
    val budget: MonthlyBudget?,
    val totalExpense: Double,
    val totalIncome: Double,
    val expenseByCategory: List<CategoryExpenseSum>,
    val monthExpenseTrend: List<DayExpenseSum>,
) {
    /** 当月结余 = 收入 - 支出 */
    val balance: Double get() = totalIncome - totalExpense

    /** 已用预算比例（0..1+）；预算为 null 或 0 时返回 0 */
    val usedRatio: Double
        get() {
            val total = budget?.totalAmount ?: return 0.0
            if (total <= 0.0) return 0.0
            return totalExpense / total
        }
}

/** 分类支出汇总 */
data class CategoryExpenseSum(
    val categoryId: Long,
    val total: Double,
)

/** 按日支出汇总 */
data class DayExpenseSum(
    /** 当天 EpochDay（LocalDate.toEpochDay()） */
    val dayBucket: Long,
    val total: Double,
)
