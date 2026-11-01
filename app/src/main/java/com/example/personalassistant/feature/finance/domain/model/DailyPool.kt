package com.example.personalassistant.feature.finance.domain.model

/**
 * Phase 7a：每日零钱池领域模型（核心特色功能）
 *
 * - [date] 当天 00:00 时间戳
 * - [dailyBudget] 当日预算
 * - [remainingAmount] 当前剩余（可为负数，表示超支）
 * - [totalSpentToday] / [totalIncomeToday] 今日累计收支
 * - [carryOver] 从昨日结转过来的金额
 * - [lastUpdated] 最后更新时间（毫秒时间戳）
 *
 * 写入策略：消费 transaction 与 daily_pools 必须在同一 Room 事务中更新
 * （参考说明书 6.7 零钱池专项注意点，由 FinanceDao.recordExpense / recordIncome 保证）。
 */
data class DailyPool(
    val id: Long = 0L,
    val date: Long,
    val dailyBudget: Double,
    val remainingAmount: Double,
    val totalSpentToday: Double = 0.0,
    val totalIncomeToday: Double = 0.0,
    val carryOver: Double = 0.0,
    val lastUpdated: Long = 0L,
)
