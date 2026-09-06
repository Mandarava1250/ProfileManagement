package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import javax.inject.Inject

/**
 * Phase 7a：刷新零钱池 UseCase（核心）
 *
 * 由两处调用：
 * 1. DailyPoolRefreshWorker.doWork() —— Worker 跑完时再调一次 scheduleNext() 续约
 * 2. PersonalAssistantApp.onCreate() —— 冷启动兜底
 *
 * 业务计算全部在 [FinanceRepository.refreshDailyPool] 内完成（含 carryOver 算法）。
 */
class RefreshDailyPoolUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(today: Long = System.currentTimeMillis()) {
        repository.refreshDailyPool(today)
    }
}
