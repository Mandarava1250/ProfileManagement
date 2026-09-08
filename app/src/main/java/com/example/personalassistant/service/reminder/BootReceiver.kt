package com.example.personalassistant.service.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.personalassistant.core.holiday.di.HolidayReminderScheduler
import com.example.personalassistant.feature.task.domain.reminder.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 开机广播接收器：BOOT_COMPLETED / MY_PACKAGE_REPLACED 时触发
 *
 * Phase 5 行为：
 * - 清空旧任务提醒（cancelAll）
 * - 由 [com.example.personalassistant.feature.task.data.repository.TaskRepositoryImpl] 的观察流
 *   在应用首次进入 UI 时自动 reschedule，无需在此枚举任务（避免 BroadcastReceiver 内做重 IO）
 *
 * Phase 7a 增量：
 * - 调度下一日的 [DailyPoolRefreshWorker]（WorkManager 被国产 ROM 杀掉后，重启时需重设）
 *
 * Phase 8 Sub-step 7 增量：
 * - 重设 [HolidayReminderScheduler]（提前通知 Worker），用用户在 Settings 里设置的时间
 * - 不主动设置 hour/minute（默认值 21:00 由 [HolidayReminderSchedulerImpl] 内部处理）
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var dailyPoolScheduler: DailyPoolRefreshScheduler
    @Inject lateinit var holidayReminderScheduler: HolidayReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Device booted / app replaced, clearing stale reminders & rescheduling...")
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        reminderScheduler.cancelAll()
                        dailyPoolScheduler.scheduleNext()
                        // 重新调度节假日提前通知 Worker，使用 Settings 里的默认时间
                        holidayReminderScheduler.scheduleEveningReminder()
                    } catch (e: Exception) {
                        Log.w(TAG, "BootReceiver action failed: ${e.message}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
