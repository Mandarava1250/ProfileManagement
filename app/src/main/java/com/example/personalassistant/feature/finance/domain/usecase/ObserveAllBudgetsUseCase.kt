package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * C 重构阶段 10：观察全部月度预算（按 yearMonth DESC）
 *
 * 拆出独立 use case 是为了让 BudgetViewModel 在「切月份查看历史预算」时
 * 走类型安全的入口，而不是直接注入 Repository。
 */
class ObserveAllBudgetsUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    operator fun invoke(): Flow<List<MonthlyBudget>> = repository.observeAllBudgets()
}