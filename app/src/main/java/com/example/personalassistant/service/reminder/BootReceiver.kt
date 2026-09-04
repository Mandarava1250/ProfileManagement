package com.example.personalassistant.service.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机广播接收器：BOOT_COMPLETED / MY_PACKAGE_REPLACED 时触发
 *
 * 后续 Phase 6 实现：
 * - 重新调度每日 00:01 的零钱池刷新任务（WorkManager PeriodicWorkRequest）
 * - 重新注册所有课程/任务的精确闹钟
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "Device booted / app replaced, rescheduling reminders...")
                // TODO Phase 6: 重新调度所有 WorkManager + AlarmManager 任务
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
