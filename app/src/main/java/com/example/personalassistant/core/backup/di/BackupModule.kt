package com.example.personalassistant.core.backup.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personalassistant.core.backup.scheduler.DailyAutoBackupWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 4：备份模块 Hilt 绑定
 *
 * 提供：
 * - kotlinx.serialization Json 实例（所有备份组件共享）
 * - 自动备份 WorkManager 调度入口（schedule / cancel）
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

    /** 自动备份 WorkManager 唯一名 */
    internal const val WORK_NAME_DAILY_BACKUP = "cyrene-daily-backup"

    @Provides
    @Singleton
    @BackupJson
    fun provideBackupJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * 调度每日自动备份
     *
     * - [BackupWorkerScheduler] 暴露给 ViewModel 调用
     * - 24h 周期 + 不需要网络（数据全在本地）
     */
    @Provides
    @Singleton
    fun provideBackupWorkerScheduler(
        @ApplicationContext context: Context,
    ): BackupWorkerScheduler {
        return BackupWorkerSchedulerImpl(context)
    }
}

/** 备份模块专用的 Json 实例 qualifier */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class BackupJson

/**
 * Worker 调度接口
 */
interface BackupWorkerScheduler {
    /** 调度每日自动备份（首次调度，会立即触发一次） */
    fun scheduleDaily()

    /** 取消自动备份 */
    fun cancelDaily()
}

/**
 * Worker 调度器实现
 */
class BackupWorkerSchedulerImpl @javax.inject.Inject constructor(
    private val context: Context,
) : BackupWorkerScheduler {

    override fun scheduleDaily() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<DailyAutoBackupWorker>(
            repeatInterval = 24, repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BackupModule.WORK_NAME_DAILY_BACKUP,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancelDaily() {
        WorkManager.getInstance(context).cancelUniqueWork(BackupModule.WORK_NAME_DAILY_BACKUP)
    }
}
