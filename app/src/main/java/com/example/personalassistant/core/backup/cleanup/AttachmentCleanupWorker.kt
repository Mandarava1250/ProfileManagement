package com.example.personalassistant.core.backup.cleanup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 8 Sub-step 5（plan 8c.2）：附件孤儿文件清理 Worker
 *
 * 薄壳 —— 真正的逻辑在 [AttachmentCleanupLogic]。本类只负责：
 * 1. 把 WorkManager 调度的 `doWork()` 转发给 Logic
 * 2. 把 IO 操作放到 `Dispatchers.IO`（Logic 是纯逻辑，不绑 dispatcher）
 * 3. 返回 Result（成功 / 重试）
 *
 * ## 调度
 * 24h 周期跑一次（[com.example.personalassistant.core.backup.di.BackupModule.WORK_NAME_ATTACHMENT_CLEANUP]）。
 * 用户开 app 时由 [com.example.personalassistant.core.backup.di.AttachmentCleanupScheduler] 注册。
 *
 * ## 失败策略
 * - 单文件删除失败 → 跳过继续，不让整批任务失败（避免一颗老鼠屎坏一锅汤）
 * - Logic.findOrphans() 抛异常 → `Result.retry()`（WorkManager 默认 30s 后重试）
 */
@HiltWorker
class AttachmentCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val logic: AttachmentCleanupLogic,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { logic.cleanup() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }
}