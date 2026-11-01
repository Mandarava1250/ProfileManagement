package com.example.personalassistant.feature.task.data.reminder

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 5 真实实现（WorkManager 分支）。
 *
 * 调度策略：
 * - 用 OneTimeWorkRequest + initialDelay
 * - UniqueWork name = "task-reminder-{taskId}"，policy = REPLACE（覆盖旧的）
 * - cancel(taskId) → cancelUniqueWork(name)
 * - cancelAll() → cancelAllWorkByTag(TAG)
 *
 * 适用场景：
 * - 延迟较长（> 1 小时）且不要求精确触发
 * - 进程已死也能唤醒执行
 *
 * 复合调度器 [CompositeReminderScheduler] 会在延迟 ≤ 1 小时时改走 AlarmManager。
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    override suspend fun schedule(info: ReminderInfo) {
        val now = Instant.now()
        val delay = Duration.between(now, info.triggerAt)
        if (delay.isNegative || delay.isZero) {
            // 已经过期：直接放弃调度（不让 Worker 立刻跑完一遍）
            Log.w(TAG, "triggerAt already past (${info.triggerAt}), skip scheduling for task=${info.taskId}")
            return
        }
        val data = Data.Builder()
            .putLong(TaskReminderWorker.KEY_TASK_ID, info.taskId)
            .putString(TaskReminderWorker.KEY_TITLE, info.title)
            .putString(TaskReminderWorker.KEY_BODY, info.body)
            .putString(TaskReminderWorker.KEY_TRIGGER_AT, info.triggerAt.toString())
            .build()
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(data)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(
            uniqueName(info.taskId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Log.i(TAG, "🟦 [WorkManager] scheduled task=${info.taskId} delay=${delay} triggerAt=${info.triggerAt}")
    }

    override suspend fun cancel(taskId: Long) {
        workManager.cancelUniqueWork(uniqueName(taskId))
        Log.i(TAG, "🟦 [WorkManager] cancelled task=$taskId")
    }

    override suspend fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
        Log.i(TAG, "🟦 [WorkManager] cancelled all")
    }

    companion object {
        private const val TAG = "TaskReminderWM"
        private const val UNIQUE_PREFIX = "task-reminder-"
        internal fun uniqueName(taskId: Long): String = "$UNIQUE_PREFIX$taskId"
    }
}
