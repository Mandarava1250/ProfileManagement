package com.example.personalassistant.core.reset

import kotlinx.coroutines.flow.SharedFlow

/**
 * 阶段 Phase 6：清除数据的应用层门面接口。
 *
 * 暴露给 SettingsViewModel 用，让 UI 层不接触 scheduler / DataStore / Room 这些底层。
 */
interface ResetRepository {

    /** UI 监听这个 Flow 拿到「清除完成」事件，弹 Toast / 更新状态。 */
    val resetEvents: SharedFlow<ResetEvent>

    /**
     * 执行清除：调度层 cancel + 数据层清空。
     *
     * @return 各步骤成功状态，UI 据此显示「全部成功」或「部分步骤失败」。
     */
    suspend fun resetAll(): ResetReport
}

/**
 * 重置过程中上报的事件（目前只发 Completed；将来想加 progress / partial 也走这里）。
 */
sealed interface ResetEvent {
    data class Completed(val report: ResetReport) : ResetEvent
}

/**
 * 数据清执行子的边界接口（fun interface 让测试里能直接用 lambda 注入）。
 *
 * 生产由 [AppDataReset] 实现；JVM 测试里写 `ResetRepository.DataResetExecutor { ... }`
 * 即可绕开 Room / DataStore 依赖。
 */
fun interface DataResetExecutor {
    suspend fun resetAll(): ResetReport
}