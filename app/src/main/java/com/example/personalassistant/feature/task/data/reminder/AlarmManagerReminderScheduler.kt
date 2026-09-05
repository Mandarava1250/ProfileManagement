package com.example.personalassistant.feature.task.data.reminder

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
 * Phase 5 真实实现（AlarmManager 分支）。
 *
 * 调度策略：
 * - SDK ≥ S：能 scheduleExactAlarms 就用 setExactAndAllowWhileIdle，否则降级 set()
 * - SDK < S：直接 setExactAndAllowWhileIdle
 * - requestCode = taskId 低 31 位 + 偏移 50_000，避免与其他模块冲突
 * - data uri 用 task://{taskId} 让 Intent 不同则 PendingIntent 不复用（防止 FLAG_UPDATE_CURRENT 行为错乱）
 *
 * 适用场景：
 * - 短延迟（≤ 1 小时）且要求精确触发
 * - 复合调度器 [CompositeReminderScheduler] 在这里判定阈值
 */
@Singleton
class AlarmManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
) : ReminderScheduler {

    override suspend fun schedule(info: ReminderInfo) {
        val triggerMillis = info.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "triggerAt already past (${info.triggerAt}), skip scheduling for task=${info.taskId}")
            return
        }
        val pi = buildPendingIntent(info.taskId, info.title, info.body)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
                    Log.w(TAG, "fallback to inexact alarm (canScheduleExactAlarms=false) for task=${info.taskId}")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
            Log.i(TAG, "🟦 [AlarmManager] scheduled task=${info.taskId} at=$triggerMillis")
        } catch (se: SecurityException) {
            Log.w(TAG, "setExactAndAllowWhileIdle rejected: ${se.message}")
        }
    }

    override suspend fun cancel(taskId: Long) {
        val pi = buildPendingIntent(taskId, "", "", mutable = false)
        alarmManager.cancel(pi)
        Log.i(TAG, "🟦 [AlarmManager] cancelled task=$taskId")
    }

    override suspend fun cancelAll() {
        // AlarmManager 没有按 tag 取消的 API；这里靠调用方在重启时主动 cancelAll(taskIds)
        Log.i(TAG, "🟦 [AlarmManager] cancelAll is a no-op (use BootReceiver to cancel by id)")
    }

    private fun buildPendingIntent(
        taskId: Long,
        title: String,
        body: String,
        mutable: Boolean = true,
    ): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_TASK_REMINDER
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskReminderReceiver.EXTRA_TITLE, title)
            putExtra(TaskReminderReceiver.EXTRA_BODY, body)
            data = android.net.Uri.parse("task://$taskId")
        }
        val requestCode = REQUEST_CODE_BASE + (taskId and 0x7FFF_FFFFL).toInt()
        val flags = if (mutable) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    companion object {
        private const val TAG = "TaskReminderAlarm"
        private const val REQUEST_CODE_BASE = 50_000
    }
}
