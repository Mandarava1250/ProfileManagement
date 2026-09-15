package com.example.personalassistant.core.backup.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.personalassistant.core.backup.cleanup.AttachmentCleanupWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import com.example.personalassistant.core.storage.AttachmentStore
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.core.backup.cleanup.AttachmentCleanupLogic

/**
 * Phase 8 Sub-step 5（plan 8c.2）：附件孤儿清理 Hilt 绑定
 *
 * - [AttachmentCleanupLogic]：3 个依赖（File + lambda + lambda）通过本模块用 @Provides 装出来
 * - [AttachmentCleanupScheduler]：调度入口（仿 [BackupWorkerSchedulerImpl] 模式）
 *
 * 与 [BackupModule] 拆成两个文件是为了让模块不会膨胀：
 * BackupModule 管备份 + 还原；本模块只管附件清理。
 */
@Module
@InstallIn(SingletonComponent::class)
object AttachmentCleanupModule {

    /** 附件清理 Worker 唯一名（24h 周期） */
    internal const val WORK_NAME_ATTACHMENT_CLEANUP = "cyrene-attachment-cleanup"

    @Provides
    @Singleton
    fun provideAttachmentCleanupLogic(
        @ApplicationContext context: Context,
        attachmentStore: AttachmentStore,
        financeDao: FinanceDao,
    ): AttachmentCleanupLogic {
        // 反射拿 attachmentStore.attachmentDir —— 它是私有 getter。
        // 与其 refactor AttachmentStore 暴露内部目录，不如直接在生产构造里再算一次
        // （同一公式：filesDir + "attachments"）。如果未来 AttachmentStore 改了子目录名，
        // 本公式会跟它脱钩 —— 届时把 AttachmentStore 的子目录常量改成 public 即可。
        val dir = File(context.filesDir, "attachments")
        return AttachmentCleanupLogic(
            attachmentDir = dir,
            referencedUrisProvider = { financeDao.allAttachmentUris() },
            fileDeleter = { fileName -> File(dir, fileName).delete() },
        )
    }

    @Provides
    @Singleton
    fun provideAttachmentCleanupScheduler(
        @ApplicationContext context: Context,
    ): AttachmentCleanupScheduler = AttachmentCleanupSchedulerImpl(context)
}

/**
 * 附件清理 Worker 调度入口（暴露给 [com.example.personalassistant.PersonalAssistantApp] 启动时注册）
 */
interface AttachmentCleanupScheduler {
    /** 调度附件清理周期任务（首次调度会立即触发一次） */
    fun schedulePeriodic()

    /** 取消 */
    fun cancel()
}

class AttachmentCleanupSchedulerImpl @javax.inject.Inject constructor(
    private val context: Context,
) : AttachmentCleanupScheduler {

    override fun schedulePeriodic() {
        // 不需要网络 / 不需要充电 —— 用户每天开 app 时跑就够
        val constraints = Constraints.Builder().build()

        val request = PeriodicWorkRequestBuilder<AttachmentCleanupWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AttachmentCleanupModule.WORK_NAME_ATTACHMENT_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(
            AttachmentCleanupModule.WORK_NAME_ATTACHMENT_CLEANUP,
        )
    }
}

/** 备用：测试时如果想替换 attachmentDir 子目录名，可打这个 qualifier */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class AttachmentCleanupDir