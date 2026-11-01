package com.example.personalassistant.feature.settings

import kotlinx.coroutines.flow.Flow

/**
 * 记账账期的「月截止日」设置
 *
 * ## 为什么要抽成接口
 *
 * [SettingsRepository] 是**具体类**，直接依赖它意味着：
 * 凡是依赖它的层（ViewModel / UseCase / Repository）都**没法单测**——
 * 一旦碰到截止日的写入，协程就会挂在 DataStore 的真实 IO 上，
 * `runTest` 的虚拟时间推不动它，测试只能靠 sleep 猜时机（必然 flaky）。
 *
 * 抽成接口后：
 * - 生产环境由 [SettingsRepository] 实现（DataStore 落盘）
 * - 单测里用内存 fake，零 IO，行为完全确定
 *
 * ## 为什么只抽这三个成员
 *
 * 其它设置（备份、提醒时间等）没有"跨层写"的需求，
 * 保持直接依赖 [SettingsRepository] 即可，不做无谓抽象。
 */
interface FinanceCutoffSettings {

    /** 用户选的截止日（1-28），默认 1 = 自然月 */
    val financeMonthCutoffDay: Flow<Int>

    /** 新截止日从哪个 ym 开始生效（"yyyy-MM"），默认 "1970-01" = 一直生效 */
    val financeMonthCutoffEffectiveFrom: Flow<String>

    /**
     * 写入月截止日
     *
     * @param day 截止日（1-28）
     * @param effectiveFromYm 新规则从哪个 ym 开始生效
     */
    suspend fun setFinanceMonthCutoff(day: Int, effectiveFromYm: String)
}
