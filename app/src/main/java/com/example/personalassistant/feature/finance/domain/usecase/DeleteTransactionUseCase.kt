package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7a：删除流水 UseCase
 *
 * - id 不存在时静默 no-op（Repository 内部已处理）
 * - 同时回滚当日零钱池（由 Repository 事务保证）
 */
class DeleteTransactionUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteTransaction(id)
    }
}
