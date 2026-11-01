package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.mapper.toDomain
import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import com.example.personalassistant.feature.finance.domain.model.MonthlyReport
import com.example.personalassistant.feature.profile.data.CurrentAccountRepository
import com.example.personalassistant.feature.settings.FinanceCutoffSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
 *
 * ## Phase 3：为什么这里也要改（计划外补的洞）
 * 这个 UseCase 是全项目**唯一**绕开 Repository 直接用 [FinanceDao] 的地方，
 * 而 DAO 的每条查询现在都要求 accountId 参数。
 * 若沿用「取一次快照」的写法，切账户后报表会停在旧账户的数据不刷新，
 * 所以这里同样用 `currentAccountIdFlow().flatMapLatest { ... }` 包一层，
 * 与 FinanceRepositoryImpl 的读路径保持同一套重发语义。
 *
 * ## 月截止日
 * 账期区间受「月截止日」设置影响（默认 1 号 = 自然月，与旧实现逐字节一致）。
 * 截止日是 Flow，所以区间要等 policy 到位再算 —— 这里用 flatMapLatest 包一层。
 */
class GetMonthlyReportUseCase @Inject constructor(
    private val financeDao: FinanceDao,
    private val currentAccountRepository: CurrentAccountRepository,
    private val settingsRepository: FinanceCutoffSettings,
) {
    /** 记账账期的月截止日策略（1-28 号，默认 1 = 自然月） */
    private val cutoffPolicyFlow: Flow<MonthCutoffPolicy> = combine(
        settingsRepository.financeMonthCutoffDay,
        settingsRepository.financeMonthCutoffEffectiveFrom,
    ) { day, effectiveFrom ->
        MonthCutoffPolicy(cutoffDay = day, effectiveFromYm = effectiveFrom)
    }

    operator fun invoke(yearMonth: String): Flow<MonthlyReport> =
        cutoffPolicyFlow.flatMapLatest { policy ->
            val (monthStart, monthEndExclusive) = BillingCycle.rangeOf(yearMonth, policy)

            currentAccountRepository.currentAccountIdFlow().flatMapLatest { accountId ->
                // 5 个 Flow combine（全部按当前账户）：
                // 1. budget：observeMonthlyBudget(accountId, yearMonth)
                // 2. expense：observeMonthlyExpense(accountId, monthStart, monthEndExclusive)
                // 3. income：observeMonthlyIncome(accountId, ...)
                // 4. byCategory：getExpenseByCategory(accountId, ...)
                // 5. trend：getExpenseTrend(accountId, ...)
                combine(
                    financeDao.observeMonthlyBudget(accountId, yearMonth),
                    financeDao.observeMonthlyExpense(accountId, monthStart, monthEndExclusive),
                    financeDao.observeMonthlyIncome(accountId, monthStart, monthEndExclusive),
                    financeDao.getExpenseByCategory(accountId, monthStart, monthEndExclusive),
                    financeDao.getExpenseTrend(accountId, monthStart, monthEndExclusive),
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
        }
}
