package com.example.personalassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.personalassistant.core.utils.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口
 *
 * - @HiltAndroidApp：Hilt 根节点，生成全局依赖图
 * - Configuration.Provider：让 WorkManager 使用 HiltWorkerFactory，让 Worker 也能注入
 */
@HiltAndroidApp
class PersonalAssistantApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /** WorkManager 配置由 Hilt 提供 WorkerFactory */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    /**
     * 注册所有通知渠道（Android 8.0+ 必需）
     * - 课程提醒
     * - 任务提醒
     * - 日程事件
     * - 零钱池刷新状态
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        listOf(
            NotificationChannel(
                NotificationHelper.CHANNEL_COURSE,
                "课程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "上课前 N 分钟自动提醒"
                enableVibration(true)
            },
            NotificationChannel(
                NotificationHelper.CHANNEL_TASK,
                "任务提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "任务截止前的提醒"
            },
            NotificationChannel(
                NotificationHelper.CHANNEL_EVENT,
                "日程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "日历事件的提醒"
            },
            NotificationChannel(
                NotificationHelper.CHANNEL_FINANCE,
                "记账与零钱池",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "零钱池刷新、月度报表等"
            }
        ).forEach { nm.createNotificationChannel(it) }
    }
}
