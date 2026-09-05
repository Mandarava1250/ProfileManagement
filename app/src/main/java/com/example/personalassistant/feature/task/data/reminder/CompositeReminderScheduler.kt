package com.example.personalassistant.feature.task.data.reminder

import android.util.Log
import com.example.personalassistant.feature.task.domain.reminder.ReminderInfo
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Named

/**
 * Phase 5 复合调度器（默认绑定到 DI）。
 *
 * 路由策略：
 * - 延迟 ≤ [SHORT_DELAY_THRESHOLD]（默认 1 小时）→ AlarmManager（精确）
 * - 延迟 > 阈值 → WorkManager（保活强、能穿透 Doze）
 * - cancel/cancelAll 同时清两个分支，避免残留
 *
 * 设计动机：
 * - WorkManager 在 Doze 下延迟可达 10+ 分钟，对短延迟场景不友好
 * - AlarmManager 精确但 SDK 31+ 需要 SCHEDULE_EXACT_ALARM 权限
 * - 复合策略让两条路各司其职
 */
@Singleton
class CompositeReminderScheduler @Inject constructor(
    @Named(NAMED_WORK) private val workManager: ReminderScheduler,
    @Named(NAMED_ALARM) private val alarmManager: ReminderScheduler,
) : ReminderScheduler {

    override suspend fun schedule(info: ReminderInfo) {
        val now = Instant.now()
        val delay = Duration.between(now, info.triggerAt)
        if (delay.isNegative || delay.isZero) {
            Log.w(TAG, "triggerAt already past, skip both branches for task=${info.taskId}")
            return
        }
        if (delay <= SHORT_DELAY_THRESHOLD) {
            Log.i(TAG, "route to AlarmManager (delay=$delay <= $SHORT_DELAY_THRESHOLD)")
            alarmManager.schedule(info)
        } else {
            Log.i(TAG, "route to WorkManager (delay=$delay > $SHORT_DELAY_THRESHOLD)")
            workManager.schedule(info)
        }
    }

    override suspend fun cancel(taskId: Long) {
        workManager.cancel(taskId)
        alarmManager.cancel(taskId)
    }

    override suspend fun cancelAll() {
        workManager.cancelAll()
        alarmManager.cancelAll()
    }

    companion object {
        private const val TAG = "CompositeReminder"
        val SHORT_DELAY_THRESHOLD: Duration = Duration.ofHours(1)
        const val NAMED_WORK = "workReminderScheduler"
        const val NAMED_ALARM = "alarmReminderScheduler"
    }
}
