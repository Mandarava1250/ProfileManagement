package com.example.personalassistant.feature.task.data.reminder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.personalassistant.core.utils.NotificationHelper

/**
 * Phase 5 任务提醒接收器（精确闹钟分支触发）。
 *
 * 由 [AlarmManagerReminderScheduler] 在 setExactAndAllowWhileIdle 触发后回调。
 * 行为：
 * - 校验 taskId/title
 * - Android 13+ 检查 POST_NOTIFICATIONS，未授权静默 return
 * - 用 [NotificationIdGenerator] 计算稳定 ID，调 [NotificationHelper] 弹通知
 */
class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TASK_REMINDER) return

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()

        if (taskId <= 0L || title.isBlank()) {
            Log.w(TAG, "invalid reminder intent taskId=$taskId title='$title'")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted, skip showing notification")
                return
            }
        }

        val notificationId = NotificationIdGenerator.forTask(taskId)
        NotificationHelper.showNotification(
            context = context,
            channelId = NotificationHelper.CHANNEL_TASK,
            notificationId = notificationId,
            title = title,
            content = body.ifBlank { "该任务到时间啦" },
        )
        Log.i(TAG, "🟦 [Receiver] fired notification id=$notificationId task=$taskId title='$title'")
    }

    companion object {
        private const val TAG = "TaskReminderReceiver"
        const val ACTION_TASK_REMINDER = "com.example.personalassistant.action.TASK_REMINDER"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}
