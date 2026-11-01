package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 7b：按关键字搜索流水 UseCase
 *
 * - 委托 [FinanceRepository.searchTransactions]
 * - 关键字为空 / 空白时 Repository 内部已 fallback 到全量（语义同 observeAllTransactions）
 * - 搜索匹配范围：备注 + 分类名（DAO 层 OR LIKE 实现）
 *
 * UI 层订阅此 UseCase 的输出，并用 [debounce]（典型 300ms）避免每帧查库。
 */
class SearchTransactionsUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    operator fun invoke(keyword: String): Flow<List<Transaction>> =
        repository.searchTransactions(keyword)
}