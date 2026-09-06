package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7b：编辑流水 UseCase
 *
 * ## 校验
 * - amount 必须 > 0
 * - categoryId 必须 > 0
 *
 * 委托 [FinanceRepository.updateTransaction]，由 Repository 调用
 * FinanceDao.transactionalUpdateTransaction（退旧 + 插新 + 同步 pool，同一事务）。
 *
 * 与 [AddTransactionUseCase] 的区别：
 * - 入参多一个 [id]（已存在的流水 id）
 * - 写入走 `updateTransaction`（事务：回滚旧 + 应用新）
 * - id 不存在时 Repository 静默 no-op，不抛异常
 */
class UpdateTransactionUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(
        id: Long,
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
    ) {
        require(amount > 0.0) { "金额必须大于 0" }
        require(categoryId > 0L) { "分类 id 非法" }
        repository.updateTransaction(
            id = id,
            type = type,
            amount = amount,
            categoryId = categoryId,
            note = note?.takeIf { it.isNotBlank() },
            date = date,
        )
    }
}