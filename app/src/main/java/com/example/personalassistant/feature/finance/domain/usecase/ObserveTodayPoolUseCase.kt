package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7a：观察当日零钱池 UseCase
 *
 * - 返回 Flow<DailyPool?>，null 表示当日 pool 还没创建（用户没设置预算 或 跨天 Worker 未跑）
 */
class ObserveTodayPoolUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    operator fun invoke() = repository.observeTodayPool()
}
