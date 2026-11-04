package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Phase 7a：刷新零钱池 UseCase（核心）
 *
 * 由两处调用：
 * 1. DailyPoolRefreshWorker.doWork() —— Worker 跑完时再调一次 scheduleNext() 续约
 * 2. PersonalAssistantApp.onCreate() —— 冷启动兜底
 *
 * 业务计算全部在 [FinanceRepository.refreshDailyPool] 内完成（含 carryOver 算法）。
 *
 * ## 批次 C：区分「定时刷新」与「手动刷新」（修帮凶 2）
 *
 * `monthly_budgets.isAutoRefresh`（预算弹窗里的「每天自动刷新零钱池」）此前**只存不读**，
 * 是个死开关 —— grep 全项目确认没有任何地方读它。现在接上：
 * - [invoke] = **定时路径**：尊重 `isAutoRefresh` —— 关掉就当天不自动重算
 * - [force] = **手动路径**：无视 `isAutoRefresh` —— 用户显式点了「刷新」就是想立刻生效
 *
 * ## 为什么手动路径不能被开关拦住
 *
 * 那个开关的语义是「每天**自动**刷新」，不是「禁止刷新」。
 * 拦住它 = 用户改完预算点「刷新」没反应 —— 而这恰恰是本批次要修的 bug之一。
 *
 * ## 为什么 Worker那一行代码不用改
 *
 * Worker 调的是 `invoke()`，而 `invoke()` 现在就是「定时路径」语义，
 * 所以 Worker 自动被这个开关管住，**不需要碰 `service/reminder/`**。
 */
class RefreshDailyPoolUseCase @Inject constructor(
    private val repository: FinanceRepository,
) {
    /**
     * 定时刷新（DailyPoolRefreshWorker / 冷启动用）
     *
     * 当月预算的 `isAutoRefresh = false` 时**跳过**。
     *
     * 预算不存在时**不**在这里跳过 —— 继续往下调，让 Repository 那边自己按
     * 「没预算 → no-op」处理（保持 Repository 是唯一判定点，避免两处各判一遍）。
     */
    suspend operator fun invoke(today: Long = System.currentTimeMillis()) {
        val budget = repository.observeCurrentMonthBudget().first()
        if (budget != null && !budget.isAutoRefresh) return
        repository.refreshDailyPool(today)
    }

    /**
     * 手动刷新（UI「刷新」按钮 / 改预算后立即重算用）
     *
     * 无视 `isAutoRefresh`。
     */
    suspend fun force(today: Long = System.currentTimeMillis()) {
        repository.refreshDailyPool(today)
    }
}
