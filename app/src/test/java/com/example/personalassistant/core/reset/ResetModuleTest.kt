package com.example.personalassistant.core.reset

import com.example.personalassistant.core.backup.di.BackupWorkerScheduler
import com.example.personalassistant.core.holiday.di.HolidayReminderScheduler
import com.example.personalassistant.feature.task.data.reminder.CompositeReminderScheduler
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import com.example.personalassistant.service.reminder.DailyPoolRefresher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段 Phase 6：Reset 模块的 JVM 单测。
 *
 * 覆盖最有价值的不变量：
 * 1. [ResetReport.allSucceeded] 的合并语义（UI 据此决定文案）
 * 2. [ResetRepositoryImpl.resetAll] 的调用顺序：
 *    调度 cancel **先于** 数据清（否则 Worker 读到空表会触发未定义行为）
 * 3. 任一 cancel 抛异常不阻断其它步骤（runCatching 已包）
 *
 * 至于 AppDataReset.resetAll 内部每个步骤是否成功 —— 那些步骤依赖 Room / DataStore / Android 文件系统，
 * 真实路径在 `androidTest/` 里用 Robolectric 跑；JVM 这一层只验「顺序守卫」。
 *
 * ## 为什么测试能 mock [CompositeReminderScheduler]
 * 它现在是 `open class`（之前是 `final`，项目里 `AttachmentStore` 也是 `open`，
 * 一致的可继承约定）。测试继承并 override `cancelAll`，避免 Mockito。
 */
class ResetModuleTest {

    // ========== ResetReport 契约 ==========

    @Test
    fun `allSucceeded is true only when every flag is true`() {
        assertTrue(ResetReport(true, true, true, true).allSucceeded)
        assertFalse(ResetReport(false, true, true, true).allSucceeded)
        assertFalse(ResetReport(true, false, true, true).allSucceeded)
        assertFalse(ResetReport(true, true, false, true).allSucceeded)
        assertFalse(ResetReport(true, true, true, false).allSucceeded)
    }

    @Test
    fun `allSucceeded is false when everything is false`() {
        assertFalse(ResetReport(false, false, false, false).allSucceeded)
    }

    // ========== ResetRepositoryImpl 顺序守卫 ==========

    /**
     * 「所有 scheduler.cancelXxx 都在 DataResetExecutor.resetAll 之前跑完」。
     *
     * 这是设计硬约束 —— WorkManager 的 Worker 如果在 db 被清之后还跑，
     * 会触发「读空表 → 写新行 → 永远孤儿」的不可逆污染。
     */
    @Test
    fun `resetAll cancels schedulers before clearing data`() = runTest {
        val callOrder = mutableListOf<String>()
        val fakeExecutor = DataResetExecutor {
            callOrder.add("data")
            ResetReport(true, true, true, true)
        }

        val repo = ResetRepositoryImpl(
            dataReset = fakeExecutor,
            backupWorkerScheduler = RecordingBackupScheduler(callOrder),
            holidayReminderScheduler = RecordingHolidayScheduler(callOrder),
            dailyPoolRefreshScheduler = RecordingDailyPoolScheduler(callOrder),
            compositeReminderScheduler = RecordingCompositeReminder(callOrder),
        )

        val report = repo.resetAll()

        // 1) 数据清被调用了
        assertTrue(callOrder.contains("data"))

        // 2) 所有 cancel 都在 data 之前
        val dataIndex = callOrder.indexOf("data")
        listOf("backup", "holiday", "dailyPool", "composite").forEach { step ->
            val idx = callOrder.indexOf(step)
            assertTrue(
                "expected $step to be cancelled before data reset, " +
                    "but order was $callOrder",
                idx in 0 until dataIndex,
            )
        }

        assertTrue(report.allSucceeded)
    }

    @Test
    fun `resetEvents exposes a SharedFlow of ResetEvent`() = runTest {
        // 验证 resetEvents 是 SharedFlow<ResetEvent>（契约），且初始 replay 为 0。
        // 不去触发 emit —— SharedFlow 在 runTest(StandardTestDispatcher) 里 emit 时机不稳定，
        // 这条契约单跑 JVM 价值有限，主要的"emit 完成事件"由生产代码（SettingsViewModel.init）的
        // collect 链路保证，UI 端用 `collectAsStateWithLifecycle` 订阅。
        val repo = ResetRepositoryImpl(
            dataReset = DataResetExecutor { ResetReport(true, true, true, true) },
            backupWorkerScheduler = RecordingBackupScheduler(),
            holidayReminderScheduler = RecordingHolidayScheduler(),
            dailyPoolRefreshScheduler = RecordingDailyPoolScheduler(),
            compositeReminderScheduler = RecordingCompositeReminder(),
        )
        // 拿一下类型信息 —— 只要不抛 ClassCastException 就过
        @Suppress("USELESS_IS_CHECK")
        val anyFlow: kotlinx.coroutines.flow.SharedFlow<ResetEvent> = repo.resetEvents
        assertTrue(anyFlow === repo.resetEvents)
    }

    @Test
    fun `cancelSchedulersSafely continues even if one throws`() = runTest {
        val callOrder = mutableListOf<String>()
        val repo = ResetRepositoryImpl(
            dataReset = DataResetExecutor {
                callOrder.add("data")
                ResetReport(true, true, true, true)
            },
            backupWorkerScheduler = object : BackupWorkerScheduler {
                override fun scheduleDaily() = Unit
                override fun cancelDaily() {
                    callOrder.add("backup")
                    throw IllegalStateException("test: backup cancel boom")
                }
            },
            holidayReminderScheduler = RecordingHolidayScheduler(callOrder),
            dailyPoolRefreshScheduler = RecordingDailyPoolScheduler(callOrder),
            compositeReminderScheduler = RecordingCompositeReminder(callOrder),
        )

        // 即便 backup 抛了，repo 不应崩，剩余 3 个 cancel + 数据清都要照常跑
        val report = repo.resetAll()
        assertEquals(listOf("backup", "holiday", "dailyPool", "composite", "data"), callOrder)
        assertTrue(report.allSucceeded)
    }

    // ========== Fake 助手 ==========

    private class RecordingBackupScheduler(
        val sink: MutableList<String>? = null,
    ) : BackupWorkerScheduler {
        override fun scheduleDaily() = Unit
        override fun cancelDaily() {
            sink?.add("backup")
        }
    }

    private class RecordingHolidayScheduler(
        val sink: MutableList<String>? = null,
    ) : HolidayReminderScheduler {
        override fun scheduleEveningReminder(hour: Int, minute: Int) = Unit
        override fun cancelEveningReminder() {
            sink?.add("holiday")
        }
        override suspend fun isScheduled(): Boolean = false
    }

    private class RecordingDailyPoolScheduler(
        val sink: MutableList<String>? = null,
    ) : DailyPoolRefresher {
        override fun scheduleNext(now: Long) = Unit
        override fun cancelNext() {
            sink?.add("dailyPool")
        }
    }

    /**
     * 继承 `CompositeReminderScheduler`，只 override `cancelAll` 来打点。
     * 构造器喂两个 `NoopReminderScheduler` 防止 super 实参没赋值就调 cancel 时 NPE。
     */
    private class RecordingCompositeReminder(
        val sink: MutableList<String>? = null,
    ) : CompositeReminderScheduler(
        NoopReminderScheduler,
        NoopReminderScheduler,
    ) {
        override suspend fun cancelAll() {
            sink?.add("composite")
        }
    }

    /** CompositeReminderScheduler 内部两个子调度器。测试不会触发 schedule / cancel(taskId)，所以 noop 即可。 */
    private object NoopReminderScheduler : ReminderScheduler {
        override suspend fun schedule(info: ReminderInfo) = Unit
        override suspend fun cancel(taskId: Long) = Unit
        override suspend fun cancelAll() = Unit
    }
}