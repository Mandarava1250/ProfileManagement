package com.example.personalassistant.feature.finance.presentation

import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.MonthlyReport
import com.example.personalassistant.feature.finance.domain.model.Transaction

/**
 * Phase 7a：Finance 主页 UI 状态
 *
 * - [isLoading] = true 表示初始数据还没回
 * - 三个数据流：daily pool / budget / transactions
 * - 派生字段：categoryById / transactionsByDay（按日期分组）
 * - [monthReport] 当月完整快照（GetMonthlyReportUseCase 输出）
 */
data class FinanceUiState(
    val isLoading: Boolean = true,
    val dailyPool: DailyPool? = null,
    val monthBudget: MonthlyBudget? = null,
    val monthExpense: Double? = null,
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val monthReport: MonthlyReport? = null,
) {
    /** 按 categoryId 查分类 */
    val categoryById: Map<Long, Category> by lazy {
        categories.associateBy { it.id }
    }

    /** 按日分组的流水（key = 当天 00:00 毫秒） */
    val transactionsByDay: List<DayGroup> by lazy {
        transactions
            .groupBy { com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils.startOfDayMillis(it.date) }
            .toSortedMap(compareByDescending { it })
            .map { (dayMillis, txs) ->
                DayGroup(
                    dayMillis = dayMillis,
                    transactions = txs.sortedByDescending { it.date },
                )
            }
    }
}

/** 单日分组：所有同一天的流水 */
data class DayGroup(
    val dayMillis: Long,
    val transactions: List<Transaction>,
) {
    val totalExpense: Double = transactions
        .filter { it.amount > 0 && it.type == com.example.personalassistant.feature.finance.domain.model.TransactionType.EXPENSE }
        .sumOf { it.amount }
    val totalIncome: Double = transactions
        .filter { it.type == com.example.personalassistant.feature.finance.domain.model.TransactionType.INCOME }
        .sumOf { it.amount }
}
