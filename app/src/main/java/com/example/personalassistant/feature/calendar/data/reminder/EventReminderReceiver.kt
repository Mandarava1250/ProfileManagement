package com.example.personalassistant.feature.calendar.data.reminder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.personalassistant.core.utils.NotificationHelper
import com.example.personalassistant.feature.task.data.reminder.NotificationIdGenerator

/**
 * Phase 8 Sub-step 1：日历事件提醒接收器（AlarmManager 分支触发）。
 *
 * 由 [EventAlarmManagerReminderScheduler] 在 setExactAndAllowWhileIdle 触发后回调。
 *
 * 行为：
 * - 校验 eventId/title
 * - Android 13+ 检查 POST_NOTIFICATIONS，未授权静默 return
 * - 用 [NotificationIdGenerator.forEvent] 计算稳定 ID，调 [NotificationHelper] 弹通知
 * - 使用渠道 [NotificationHelper.CHANNEL_EVENT]（已在 PersonalAssistantApp.onCreate 注册）
 *
 * 与 TaskReminderReceiver 的区别：
 * - action 不同（避免一个系统收到另一边的 intent）
 * - 通知 ID 段不同（forEvent 而非 forTask），不同模块不会撞码
 */
class EventReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EVENT_REMINDER) return

        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()

        if (eventId <= 0L || title.isBlank()) {
            Log.w(TAG, "invalid reminder intent eventId=$eventId title='$title'")
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

        val notificationId = NotificationIdGenerator.forEvent(eventId)
        NotificationHelper.showNotification(
            context = context,
            channelId = NotificationHelper.CHANNEL_EVENT,
            notificationId = notificationId,
            title = title,
            content = body.ifBlank { "日程即将开始" },
        )
        Log.i(TAG, "🟩 [Receiver] fired notification id=$notificationId event=$eventId title='$title'")
    }

    companion object {
        private const val TAG = "EventReminderReceiver"
        const val ACTION_EVENT_REMINDER = "com.example.personalassistant.action.EVENT_REMINDER"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
    }
}