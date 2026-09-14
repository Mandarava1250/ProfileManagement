package com.example.personalassistant.feature.calendar.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 1：日历事件的 AlarmManager 调度器。
 *
 * 与任务端 [com.example.personalassistant.feature.task.data.reminder.AlarmManagerReminderScheduler]
 * 几乎一致，唯一区别是：
 * - REQUEST_CODE_BASE = 60_000（避免与任务段 50_000 撞 PendingIntent code）
 * - 触发接收器是 [EventReminderReceiver]，action 是 ACTION_EVENT_REMINDER
 * - data uri 用 event://{id} 让不同 intent 不复用同一个 PendingIntent
 *
 * 设计取舍：
 * - 沿用任务端的 [ReminderScheduler] 接口（Event 端通过 task 模块的接口注入）
 * - ReminderInfo.taskId 字段名虽叫 "taskId"，但实际是「主键 + 通知 ID 映射源」，
 *   这里传 eventId，类型为 Long，语义中立
 * - 不复用任务端的 WorkManager 分支：事件提醒由用户主动设置，频率低，
 *   直接走 AlarmManager 精确触发 + BootReceiver 开机重登
 */
@Singleton
class EventAlarmManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
) : ReminderScheduler {

    override suspend fun schedule(info: ReminderInfo) {
        val triggerMillis = info.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "triggerAt already past (${info.triggerAt}), skip scheduling for event=${info.taskId}")
            return
        }
        val pi = buildPendingIntent(info.taskId, info.title, info.body)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                    Log.w(TAG, "fallback to inexact alarm (canScheduleExactAlarms=false) for event=${info.taskId}")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
            Log.i(TAG, "🟩 [EventAlarmManager] scheduled event=${info.taskId} at=$triggerMillis")
        } catch (se: SecurityException) {
            Log.w(TAG, "setExactAndAllowWhileIdle rejected: ${se.message}")
        }
    }

    override suspend fun cancel(eventId: Long) {
        val pi = buildPendingIntent(eventId, "", "", mutable = false)
        alarmManager.cancel(pi)
        Log.i(TAG, "🟩 [EventAlarmManager] cancelled event=$eventId")
    }

    override suspend fun cancelAll() {
        // AlarmManager 没有按 tag 取消的 API；这里靠调用方在重启时主动 cancel(eventId)
        Log.i(TAG, "🟩 [EventAlarmManager] cancelAll is a no-op (use BootReceiver to cancel by id)")
    }

    private fun buildPendingIntent(
        eventId: Long,
        title: String,
        body: String,
        mutable: Boolean = true,
    ): PendingIntent {
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            action = EventReminderReceiver.ACTION_EVENT_REMINDER
            putExtra(EventReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(EventReminderReceiver.EXTRA_TITLE, title)
            putExtra(EventReminderReceiver.EXTRA_BODY, body)
            data = android.net.Uri.parse("event://$eventId")
        }
        val requestCode = REQUEST_CODE_BASE + (eventId and 0x7FFF_FFFFL).toInt()
        val flags = if (mutable) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    companion object {
        private const val TAG = "EventReminderAlarm"

        /**
         * PendingIntent request code 基址。
         *
         * 任务端使用 50_000；这里用 60_000 拉开段位，避免同 id 撞码。
         * （其实只要 taskId != eventId 就不撞，但分段更安全）。
         */
        const val REQUEST_CODE_BASE = 60_000
    }
}