package com.example.personalassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.personalassistant.core.backup.di.AttachmentCleanupScheduler
import com.example.personalassistant.core.utils.NotificationHelper
import com.example.personalassistant.feature.finance.domain.usecase.RefreshDailyPoolUseCase
import com.example.personalassistant.service.reminder.DailyPoolRefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用入口
 *
 * - @HiltAndroidApp：Hilt 根节点，生成全局依赖图
 * - Configuration.Provider：让 WorkManager 使用 HiltWorkerFactory，让 Worker 也能注入
 *
 * Phase 7a 增量：
 * - onCreate 启动时同步触发一次零钱池刷新（兜底：Worker 错过或被国产 ROM 杀掉）
 * - 调度下一天的 WorkManager Worker
 */
@HiltAndroidApp
class PersonalAssistantApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var refreshDailyPool: RefreshDailyPoolUseCase
    @Inject lateinit var dailyPoolScheduler: DailyPoolRefreshScheduler

    // Phase 9 9b B1：启动时注册 24h 周期附件清理 Worker。
    // 接口在 core/backup/di/AttachmentCleanupModule.kt:67；实现 AttachmentCleanupSchedulerImpl
    // 用 Hilt @Provides @Singleton 装，本类只需声明 + 调一次 schedulePeriodic()。
    @Inject lateinit var attachmentCleanupScheduler: AttachmentCleanupScheduler

    /**
     * 应用级 IO scope，用于 onCreate 的后台触发。
     * SupervisorJob 让单个协程失败不影响整个 App 启动。
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        bootstrapFinance()

        // Phase 9 9b B1：调度附件清理周期任务（24h 周期，KEEP 策略）。
        // 同步调用：内部只构造 Constraints + PeriodicWorkRequest + enqueueUniquePeriodicWork，
        // 不阻塞 App 启动。失败不抛异常（WorkManager 会自己处理）。
        attachmentCleanupScheduler.schedulePeriodic()
        Log.i(TAG, "🧹 App.onCreate: scheduled attachment cleanup worker (24h periodic, KEEP)")
    }

    /**
     * Phase 7a：冷启动时刷新一次零钱池（如果当天还没创建）并注册 Worker 续约。
     */
    private fun bootstrapFinance() {
        appScope.launch {
            try {
                Log.i(TAG, "🟢 App.onCreate: refresh daily pool + schedule next worker")
                refreshDailyPool(System.currentTimeMillis())
                dailyPoolScheduler.scheduleNext(System.currentTimeMillis())
            } catch (e: Exception) {
                // 不阻断 App 启动；下次 Worker 触发或用户进 Finance tab 时再试
                Log.w(TAG, "bootstrapFinance failed: ${e.message}", e)
            }
        }
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

    companion object {
        private const val TAG = "PersonalAssistantApp"
    }
}
