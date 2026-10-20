package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7a：设置月度预算 UseCase
 *
 * - 校验 totalAmount >= 0
 * - 委托 [FinanceRepository.setMonthlyBudget]
 * - 若修改的是当月预算，Repository 内部会立即触发零钱池重算
 */
class SetMonthlyBudgetUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(
        yearMonth: String,
        totalAmount: Double,
        isAutoRefresh: Boolean = true,
        carryOverEnabled: Boolean = true,
        warningThreshold: Double? = null,
    ): Long {
        require(totalAmount >= 0.0) { "预算不能为负" }
        require(yearMonth.matches(Regex("""\d{4}-\d{2}"""))) { "yearMonth 格式必须为 yyyy-MM" }
        return repository.setMonthlyBudget(
            yearMonth = yearMonth,
            totalAmount = totalAmount,
            isAutoRefresh = isAutoRefresh,
            carryOverEnabled = carryOverEnabled,
            warningThreshold = warningThreshold,
        )
    }
}
