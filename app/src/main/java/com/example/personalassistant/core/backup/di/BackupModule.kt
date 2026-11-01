package com.example.personalassistant.core.backup.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personalassistant.core.backup.importer.BackupImporter
import com.example.personalassistant.core.backup.importer.IBackupImporter
import com.example.personalassistant.core.backup.scheduler.DailyAutoBackupWorker
import com.example.personalassistant.core.backup.snapshot.BackupCurrentSnapshotProvider
import com.example.personalassistant.core.backup.snapshot.BackupSnapshotProvider
import com.example.personalassistant.core.backup.validator.BackupValidator
import com.example.personalassistant.core.backup.validator.IBackupValidator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 4 + Phase 9 A2：备份模块 Hilt 绑定
 *
 * Hilt 不允许单个 `@Module` 同时含非静态 `@Provides` 与 abstract `@Binds`，
 * 所以拆成两个 module：
 * - [BackupBindingsModule]：`abstract class` + `@Binds` 绑接口
 * - [BackupModule]：`object` + `@Provides` 提供 Json / WorkerScheduler
 */

/** 自动备份 WorkManager 唯一名 */
internal const val WORK_NAME_DAILY_BACKUP = "cyrene-daily-backup"

/**
 * Phase 9 A2：把 [BackupCurrentSnapshotProvider]（实现类）绑到 [BackupSnapshotProvider]（接口）。
 *
 * - 让 [com.example.personalassistant.feature.settings.backup.BackupPreviewViewModel] 通过接口拿快照
 * - 测试可以传 `object : BackupSnapshotProvider` 的 fake，绕过 open class 的 9 DAO + Room 初始化
 * - 生产运行：Hilt 注入唯一的 [BackupCurrentSnapshotProvider] 单例
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupBindingsModule {

    @Binds
    @Singleton
    abstract fun bindBackupSnapshotProvider(
        impl: BackupCurrentSnapshotProvider,
    ): BackupSnapshotProvider

    /**
     * Phase 10 10a-1：把 [BackupValidator]（具体类）绑到 [IBackupValidator]（接口），
     * 与 [BackupCurrentSnapshotProvider]→[BackupSnapshotProvider] 同模式（Phase 9 A2 已建立）。
     *
     * - 让 VM 通过接口拿校验器；测试可以传 `FakeBackupValidator : IBackupValidator`
     * - 生产代码不需改业务逻辑，Hilt 注入路径仍指向同一单例
     */
    @Binds
    @Singleton
    abstract fun bindBackupValidator(
        impl: BackupValidator,
    ): IBackupValidator

    /**
     * Phase 10 10a-1：把 [BackupImporter]（具体类）绑到 [IBackupImporter]（接口）。
     *
     * 接口故意只含 VM 用到的 `importWithPreview`（不暴露 `import(InputStream)`），
     * 让 VM 通过接口拿导入器；测试可以传 `FakeBackupImporter : IBackupImporter`。
     */
    @Binds
    @Singleton
    abstract fun bindBackupImporter(
        impl: BackupImporter,
    ): IBackupImporter
}

/**
 * 提供：
 * - kotlinx.serialization Json 实例（所有备份组件共享）
 * - 自动备份 WorkManager 调度入口（schedule / cancel）
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupModule {

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
class BackupWorkerSchedulerImpl @Inject constructor(
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
            WORK_NAME_DAILY_BACKUP,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancelDaily() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DAILY_BACKUP)
    }
}