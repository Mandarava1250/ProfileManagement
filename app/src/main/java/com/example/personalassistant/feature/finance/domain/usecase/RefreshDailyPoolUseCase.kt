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
 *
 * ## 批次 D：定时路径里顺带做零钱池日切结算
 *
 * 用户新要求：「零钱池 = 今日剩余在今日结束后自动存入，用户无法自行更改余额」。
 * 结算就挂在**定时路径**（[invoke]）里，排在刷新额度**之前**：
 *
 * ```
 * invoke() → ① 结算零钱池（结算到昨天）
 *         → ② 刷新今天的日额度
 * ```
 *
 * 为什么挂在 `invoke()` 而不是 Worker 里：
 * - `invoke()` 已经是「定时语义」，Worker 和冷启动 `Application.onCreate` 都调它
 *   → **那两个文件一行都不用改**；
 * - 结算和刷新**共用 `isAutoRefresh` 开关** —— 用户关掉「每天自动刷新零钱池」时
 *   也不会自动结算，不会出现「不刷新但结算」的怪状态。
 *
 * 结算的语义细节（结算到哪一天、算多少、怎么落库、幂等）见
 * [SettleCoinPoolUseCase]。
 */
class RefreshDailyPoolUseCase @Inject constructor(
    private val repository: FinanceRepository,
    private val settleCoinPool: SettleCoinPoolUseCase,
) {
    /**
     * 定时刷新（DailyPoolRefreshWorker / 冷启动用）
     *
     * 当月预算的 `isAutoRefresh = false` 时**跳过**（连结算一起跳过）。
     *
     * 预算不存在时**不**在这里跳过 —— 继续往下调，让 Repository 那边自己按
     * 「没预算 → no-op」处理（保持 Repository 是唯一判定点，避免两处各判一遍）。
     */
    suspend operator fun invoke(today: Long = System.currentTimeMillis()) {
        val budget = repository.observeCurrentMonthBudget().first()
        if (budget != null && !budget.isAutoRefresh) return
        // 批次 D：先结算昨天的零钱池，再刷新今天的日额度。
        // 顺序有讲究：结算读的是**昨天**的 daily_pools 行，
        // 刷新写的是**今天**的行，两者不冲突，但先结算能让余额先到位。
        settleCoinPool(today)
        repository.refreshDailyPool(today)
    }

    /**
     * 手动刷新（UI「刷新」按钮 / 改预算后立即重算用）
     *
     * 无视 `isAutoRefresh`。
     *
     * ⚠️ **不结算** —— 手动重算当天额度不该篡改历史账。
     * 结算只由定时路径驱动（一天一次，结算到昨天）。
     */
    suspend fun force(today: Long = System.currentTimeMillis()) {
        repository.refreshDailyPool(today)
    }
}
