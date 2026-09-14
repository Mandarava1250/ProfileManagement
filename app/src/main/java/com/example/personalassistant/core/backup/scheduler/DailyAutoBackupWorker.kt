package com.example.personalassistant.core.backup.scheduler

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personalassistant.core.backup.exporter.BackupExporter
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupFormatVersion
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.backup.serializer.BackupSerializer
import com.example.personalassistant.feature.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Phase 8 Sub-step 4：每日自动备份 Worker
 *
 * 调度入口：
 * - [BackupModule.scheduleDailyAutoBackup] 首次调度（用户开启开关时）
 * - [BackupModule.cancelAutoBackup] 取消调度（用户关闭开关时）
 *
 * 执行流程：
 * 1. 从 [SettingsRepository] 读出 folderUri 和 autoBackup 开关
 * 2. 若开关关闭或 URI 为空 → return Result.success()（不做任何事）
 * 3. 调用 [BackupSerializer.snapshot] 拉取 Room 全表（一次）
 * 4. 通过 SAF 在 folder 下创建 ZIP 文件
 * 5. 调用 [BackupExporter.export] 写入 ZIP
 * 6. 写回 lastBackupAt 时间戳
 *
 * 失败策略：保留旧 lastBackupAt 不更新；返回 Result.retry() 让 WorkManager 重试
 */
@HiltWorker
class DailyAutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val serializer: BackupSerializer,
    private val exporter: BackupExporter,
    private val settings: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // ===== 1. 检查开关 =====
        val enabled = settings.autoBackupEnabled.first()
        if (!enabled) return Result.success()

        val folderUri = settings.backupFolderUri.first()
        if (folderUri.isBlank()) {
            // 没选目录就不备份
            return Result.success()
        }

        // ===== 2. 拉取快照（一次）=====
        val payload = when (val result = serializer.snapshot()) {
            is BackupResult.Success -> result.value
            is BackupResult.Failure -> {
                return Result.retry()
            }
        }

        // ===== 3. 写 SAF =====
        return when (writeToSaf(Uri.parse(folderUri), payload)) {
            is BackupResult.Success -> {
                settings.setLastBackupAt(System.currentTimeMillis())
                Result.success()
            }
            is BackupResult.Failure -> Result.retry()
        }
    }

    private fun writeToSaf(folderUri: Uri, payload: BackupPayload): BackupResult<Unit> {
        return try {
            val resolver: ContentResolver = applicationContext.contentResolver
            val fileName = BackupFormatVersion.defaultZipName().removeSuffix(BackupFormatVersion.ZIP_SUFFIX)
            val documentFile = DocumentFile.fromTreeUri(applicationContext, folderUri)
                ?: return BackupResult.Failure(BackupError.IO_ERROR, "无法解析 folderUri")

            val newDoc = documentFile.createFile("application/zip", fileName)
                ?: return BackupResult.Failure(BackupError.IO_ERROR, "无法在所选文件夹创建备份文件")
            val newFileUri = newDoc.uri

            val outputStream = resolver.openOutputStream(newFileUri)
                ?: return BackupResult.Failure(BackupError.IO_ERROR, "无法打开备份文件输出流")

            outputStream.use { os -> exporter.export(payload, os) }
        } catch (t: Throwable) {
            BackupResult.Failure(
                BackupError.IO_ERROR,
                t.message ?: "SAF 写入失败：${t::class.java.simpleName}",
            )
        }
    }
}
