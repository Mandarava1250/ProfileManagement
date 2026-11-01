package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7a：新增流水 UseCase
 *
 * ## 校验
 * - amount 必须 > 0
 * - categoryId 必须 > 0
 *
 * 委托 [FinanceRepository.addTransaction] 写入事务。
 */
class AddTransactionUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
        attachmentUri: String? = null,
    ): Long {
        require(amount > 0.0) { "金额必须大于 0" }
        require(categoryId > 0L) { "分类 id 非法" }
        return repository.addTransaction(
            type = type,
            amount = amount,
            categoryId = categoryId,
            note = note?.takeIf { it.isNotBlank() },
            date = date,
            attachmentUri = attachmentUri,
        )
    }
}
