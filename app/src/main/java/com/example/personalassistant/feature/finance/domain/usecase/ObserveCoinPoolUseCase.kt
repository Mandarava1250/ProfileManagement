package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.CoinPool
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * 批次 C：观察本账户的零钱池
 *
 * null = 该账户还没建过零钱池（UI 显示「还没建零钱池」而不是 ¥0.00 ——
 * 后者会让人以为「余额是0」，而实际是「根本没建」）。
 */
class ObserveCoinPoolUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    operator fun invoke() = repository.observeCoinPool()
}
