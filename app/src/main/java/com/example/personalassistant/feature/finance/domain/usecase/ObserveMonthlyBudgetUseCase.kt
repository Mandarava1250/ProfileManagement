package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7a：观察当月预算 UseCase
 */
class ObserveMonthlyBudgetUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    operator fun invoke(): kotlinx.coroutines.flow.Flow<MonthlyBudget?> =
        repository.observeCurrentMonthBudget()
}
