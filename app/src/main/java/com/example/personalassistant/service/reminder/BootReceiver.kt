package com.example.personalassistant.service.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
 * Phase 5 实现：
 * - 清空旧提醒（cancelAll）
 * - 由 [com.example.personalassistant.feature.task.data.repository.TaskRepositoryImpl] 的观察流
 *   在应用首次进入 UI 时自动 reschedule，无需在此枚举任务（避免 BroadcastReceiver 内做重 IO）
 *
 * 设计动机：
 * - BR 生命周期只有 10 秒，做重 IO 会被杀
 * - Compose UI 启动后会自动观察 task 流 + 重新调度（应用启动 path）
 * - 这里只清旧提醒防止「重启后弹出陈旧通知」
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderScheduler: ReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Device booted / app replaced, clearing stale reminders...")
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        reminderScheduler.cancelAll()
                    } catch (e: Exception) {
                        Log.w(TAG, "cancelAll on boot failed: ${e.message}")
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
