package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget

/**
 * 今日概览 —— 日历「日」视图顶部那条横条的数据
 *
 * 放在 presentation 层而不是 domain 层：它是**为日历页服务的视图模型**，
 * 不是日历这个领域自己的概念。domain 层不因此依赖 finance 模块。
 *
 * @param monthBudget 本月预算（null = 用户没设预算）
 * @param dailyPool 今日零钱池（null = 今天还没生成）
 * @param monthSpent 本月已花（null = 拿不到账期口径的支出）
 */
data class TodayOverview(
    val monthBudget: MonthlyBudget? = null,
    val dailyPool: DailyPool? = null,
    val monthSpent: Double? = null,
) {
    /**
     * 本月预算还剩多少
     *
     * 任一数据缺失 → null。横条据此隐藏这一段，
     * 而不是显示「剩余 0」这种会误导人的假信息。
     */
    val budgetRemaining: Double?
        get() {
            val budget = monthBudget ?: return null
            val spent = monthSpent ?: return null
            return budget.totalAmount - spent
        }

    /** 已用比例（0..1+），null = 无法计算 */
    val usedRatio: Double?
        get() {
            val budget = monthBudget ?: return null
            val spent = monthSpent ?: return null
            if (budget.totalAmount <= 0.0) return null
            return spent / budget.totalAmount
        }

    /** 预算是否已经超支 */
    val isOverBudget: Boolean
        get() = usedRatio != null && usedRatio!! > 1.0
}
