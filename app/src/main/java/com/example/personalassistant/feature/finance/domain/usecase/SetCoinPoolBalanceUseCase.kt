package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * 批次 C：直接设置零钱池余额（纠正误操作用）
 *
 * 与 [AdjustCoinPoolUseCase] 的区别：一个是「相对变动」，一个是「绝对赋值」。
 * 两者都允许余额为负。
 */
class SetCoinPoolBalanceUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(balance: Double, note: String? = null): Double =
        repository.setCoinPoolBalance(balance = balance, note = note)
}
