package com.example.personalassistant.service.reminder

/**
 * 阶段 Phase 6：每日零钱池刷新的「取消/调度」操作边界。
 *
 * 把 [DailyPoolRefreshScheduler] 里的两个方法抽到本接口，是因为 [ResetRepositoryImpl]
 * 的 JVM 单测需要 mock 一个能被注入的对象 —— 它原本是 final class + 需要 Android Context
 * 构造参数，没法直接 new。
 *
 * 接口窄、签名稳定；具体实现仍是 [DailyPoolRefreshScheduler]。
 */
interface DailyPoolRefresher {
    /** 调度下一个 00:01 的刷新任务。默认参数在接口声明，override 不能再写默认值（Kotlin 限制）。 */
    fun scheduleNext(now: Long)

    /** 取消已调度的任务 */
    fun cancelNext()
}