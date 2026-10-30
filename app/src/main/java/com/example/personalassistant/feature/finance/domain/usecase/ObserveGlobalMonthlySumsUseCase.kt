package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.GlobalMonthlySums
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察全账户合并的当月总额 —— Phase 14
 *
 * 日历页的「今日概览」要用「本月已花」算预算剩余。
 * 这里不直接用 [GetMonthlyReportUseCase]：那个用例会走账期策略（截止日）、
 * 拼分类汇总和趋势，代价大得多；预算横条只要一个总额。
 *
 * 注意口径差异：本 UseCase 是**自然月**（跟 `observeCurrentMonthBudget` 对齐），
 * 账期策略那一套留给记账页自己用。
 */
class ObserveGlobalMonthlySumsUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    operator fun invoke(): Flow<GlobalMonthlySums?> = repository.observeGlobalMonthlySums()
}
