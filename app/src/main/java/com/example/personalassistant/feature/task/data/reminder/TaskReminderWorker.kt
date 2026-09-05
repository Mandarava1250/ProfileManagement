package com.example.personalassistant.feature.task.data.reminder

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personalassistant.core.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Phase 5 任务提醒 Worker —— WorkManager 触发时弹通知。
 *
 * 入参（来自 WorkManagerReminderScheduler.schedule）：
 * - KEY_TASK_ID: Long
 * - KEY_TITLE: String
 * - KEY_BODY: String
 * - KEY_TRIGGER_AT: String（Instant.toString()）
 *
 * Android 13+ 通知权限检查：WorkManager 进程默认没有 UI 上下文，
 * 所以这里只做 checkSelfPermission 检查；如未授权直接 Result.success() 退出，不弹通知。
 */
@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val body = inputData.getString(KEY_BODY).orEmpty()
        if (taskId <= 0L || title.isBlank()) {
            Log.w(TAG, "invalid input, taskId=$taskId title='$title'")
            return Result.success()
        }
        return try {
            val notificationId = NotificationIdGenerator.forTask(taskId)
            NotificationHelper.showNotification(
                context = applicationContext,
                channelId = NotificationHelper.CHANNEL_TASK,
                notificationId = notificationId,
                title = title,
                content = body.ifBlank { "该任务到时间啦" },
            )
            Log.i(TAG, "🟦 [Worker] fired notification id=$notificationId task=$taskId title='$title'")
            Result.success()
        } catch (se: SecurityException) {
            // 通知权限被撤销
            Log.w(TAG, "Notification permission revoked: ${se.message}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed for task=$taskId", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TaskReminderWorker"
        const val KEY_TASK_ID = "taskId"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_TRIGGER_AT = "triggerAt"
    }
}
