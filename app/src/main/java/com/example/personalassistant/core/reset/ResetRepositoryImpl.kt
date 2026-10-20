package com.example.personalassistant.core.reset

import com.example.personalassistant.core.backup.di.BackupWorkerScheduler
import com.example.personalassistant.core.holiday.di.HolidayReminderScheduler
import com.example.personalassistant.feature.task.data.reminder.CompositeReminderScheduler
import com.example.personalassistant.service.reminder.DailyPoolRefresher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阶段 Phase 6：清除数据的应用层门面。
 *
 * ## 设计动机
 * SettingsViewModel 不应该直接依赖 4 个 scheduler（不同模块拼凑起来泄漏给 UI），
 * 也不应该依赖 AppDataReset 直接做 IO。Repository 把「数据层清 + 调度层 cancel」
 * 包成一个 [resetAll] 方法，并发一个 [resetEvents] 让 UI 监听「某步骤完成 / 失败」。
 *
 * ## 调度与闹钟清理
 * | 系统 | 是否处理 | 处理方式 |
 * |---|---|---|
 * | BackupWork (WorkManager) | ✅ | [BackupWorkerScheduler.cancelDaily] |
 * | HolidayEveningReminderWorker | ✅ | [HolidayReminderScheduler.cancelEveningReminder] |
 * | DailyPoolRefreshWorker | ✅ | [DailyPoolRefreshScheduler.cancelNext] |
 * | AttachmentCleanupWorker | ✅（WorkManager 的 cancelAllWorkByTag 会一并清） | 由 WorkManager 层自动处理 |
 * | TaskReminder (WorkManager + AlarmManager) | ✅ | [CompositeReminderScheduler.cancelAll] |
 * | EventReminder (AlarmManager) | ⚠️ no-op | AlarmManager 没有 tag cancel API，靠 `BootReceiver` 下次开机按空表重建 |
 *
 * 任务 / 事件的 AlarmManager 残留闹钟是已知残留 —— UI 在 [resetAll] 成功后建议用户「重启一次 App」，
 * 这是 README 文档级「已知约束」，不是 bug。
 *
 * ## 测试性
 * 构造参数 [dataReset] 用 [DataResetExecutor] 接口（而不是具体类 `AppDataReset`），
 * 是因为后者依赖 Room / DataStore / Android 文件系统（JVM 上无法 new），但我们要测的是
 * 「cancel 先于 data」的顺序守卫 —— 用 lambda 实现 [DataResetExecutor] 即可纯 JVM 跑。
 */
@Singleton
class ResetRepositoryImpl @Inject constructor(
    private val dataReset: DataResetExecutor,
    private val backupWorkerScheduler: BackupWorkerScheduler,
    private val holidayReminderScheduler: HolidayReminderScheduler,
    private val dailyPoolRefreshScheduler: DailyPoolRefresher,
    private val compositeReminderScheduler: CompositeReminderScheduler,
) : ResetRepository {

    /**
     * `extraBufferCapacity = 4` + `onBufferOverflow = DROP_OLDEST`：UI 短暂不订阅时
     * 不阻塞发送端（resetAll 不挂起）。该事件流是「单次信号」（每次 resetAll 推一个 Completed），
     * 漏掉一次即过时 —— 不需要 backpressure。
     */
    private val _events = MutableSharedFlow<ResetEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    override val resetEvents: SharedFlow<ResetEvent> = _events.asSharedFlow()

    override suspend fun resetAll(): ResetReport {
        // 1) 调度层 cancel（WorkManager / AlarmManager）—— 必须在数据层之前，
        //    否则 Worker 跑起来读到一个被清空的 db 会触发未定义行为。
        cancelSchedulersSafely()

        // 2) 数据层清（Room + DataStore + 文件 IO）
        val report = dataReset.resetAll()

        // 3) 上报事件
        _events.emit(ResetEvent.Completed(report))
        return report
    }

    private suspend fun cancelSchedulersSafely() {
        // 每个 cancel 都包 runCatching，单个失败不阻断其它 —— 清除场景里我们更想要
        // 「部分完成」而不是「一步失败全盘不跑」。
        runCatching { backupWorkerScheduler.cancelDaily() }
        runCatching { holidayReminderScheduler.cancelEveningReminder() }
        runCatching { dailyPoolRefreshScheduler.cancelNext() }
        runCatching { compositeReminderScheduler.cancelAll() }
    }
}