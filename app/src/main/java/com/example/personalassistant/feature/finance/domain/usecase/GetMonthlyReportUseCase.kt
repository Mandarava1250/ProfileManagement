package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.mapper.toDomain
import com.example.personalassistant.feature.finance.domain.model.MonthlyReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Phase 7a：组装月度报表 UseCase
 *
 * - 输入：yearMonth（格式 yyyy-MM，例 "2026-09"）
 * - 输出：Flow<MonthlyReport>，每次 budget / expense / income / byCategory / trend 任一变化都重新组装
 *
 * ## 实现动机
 * - MonthlyReport 是纯展示层 model，组装逻辑应在 domain 层（而不是 Repository）
 * - 注入 FinanceDao 而不是 Repository，避免给 Repository 暴露过多面向报表的接口
 */
class GetMonthlyReportUseCase @Inject constructor(
    private val financeDao: FinanceDao,
) {
    operator fun invoke(yearMonth: String): Flow<MonthlyReport> {
        val (monthStart, monthEndExclusive) = computeMonthRange(yearMonth)

        // 5 个 Flow combine：
        // 1. budget：observeMonthlyBudget(yearMonth)
        // 2. expense：observeMonthlyExpense(monthStart, monthEndExclusive)
        // 3. income：observeMonthlyIncome(monthStart, monthEndExclusive)
        // 4. byCategory：getExpenseByCategory(monthStart, monthEndExclusive)
        // 5. trend：getExpenseTrend(monthStart, monthEndExclusive)
        return combine(
            financeDao.observeMonthlyBudget(yearMonth),
            financeDao.observeMonthlyExpense(monthStart, monthEndExclusive),
            financeDao.observeMonthlyIncome(monthStart, monthEndExclusive),
            financeDao.getExpenseByCategory(monthStart, monthEndExclusive),
            financeDao.getExpenseTrend(monthStart, monthEndExclusive),
        ) { budgetEntity, expense, income, byCategory, trend ->
            MonthlyReport(
                yearMonth = yearMonth,
                budget = budgetEntity?.toDomain(),
                totalExpense = expense ?: 0.0,
                totalIncome = income ?: 0.0,
                expenseByCategory = byCategory.map { it.toDomain() },
                monthExpenseTrend = trend.map { it.toDomain() },
            )
        }
    }

    private fun computeMonthRange(yearMonth: String): Pair<Long, Long> {
        val ymObj = YearMonth.parse(yearMonth)
        val zone = ZoneId.systemDefault()
        val start = ymObj.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endExclusive = ymObj.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to endExclusive
    }
}
