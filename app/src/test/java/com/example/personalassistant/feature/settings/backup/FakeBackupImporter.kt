package com.example.personalassistant.feature.settings.backup

import com.example.personalassistant.core.backup.importer.IBackupImporter
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupPreview
import com.example.personalassistant.core.backup.model.BackupResult

/**
 * Phase 10 10a-1：备份导入器的 Fake
 *
 * 实现 [IBackupImporter] 接口（Phase 10 抽出），直接返回测试预设结果，
 * 跳过真 Room 事务 / 9 个 DAO / `reconcileCurrentAccount`。
 *
 * ## 用法
 * - 默认 `nextResult = Success(BackupPayload())`（总记录数 0）
 * - 调 [setSuccess] 注入带数据的 payload（让 VM 走 `Imported(totalRecords=N)`）
 * - 调 [setFailure] 让 VM 走 `Error(IMPORT_FAILED, ...)` 分支
 *
 * ## 设计取舍
 * - Phase 10 之前是继承 `open class BackupImporter` + 覆写 `importWithPreview`。
 *   但 `importWithPreview` 默认 final 无法 override；且父类构造需要 `AppDatabase` /
 *   `CurrentAccountRepository` 等非空依赖，JVM 单测里只能用 unchecked-cast 占位。
 * - Phase 10 改成接口化：生产代码抽 `IBackupImporter` 接口 → Fake 直接 `:` 实现，
 *   **零继承、无 hack**。
 */
class FakeBackupImporter : IBackupImporter {

    private var nextResult: BackupResult<BackupPayload> = BackupResult.Success(BackupPayload())

    /** importWithPreview 被调用的次数 */
    var importWithPreviewCallCount: Int = 0
        private set

    /** 最近一次传入 importWithPreview 的 payload（用于断言 VM 状态机传给 import 的内容） */
    var lastPayload: BackupPayload? = null
        private set

    /** 最近一次传入 importWithPreview 的 preview */
    var lastPreview: BackupPreview? = null
        private set

    override suspend fun importWithPreview(
        payload: BackupPayload,
        preview: BackupPreview,
    ): BackupResult<BackupPayload> {
        importWithPreviewCallCount++
        lastPayload = payload
        lastPreview = preview
        return nextResult
    }

    /**
     * 让下次 `importWithPreview` 返回 Success。默认 payload 总记录数 = 0。
     */
    fun setSuccess(payload: BackupPayload = BackupPayload()) {
        nextResult = BackupResult.Success(payload)
    }

    /**
     * 让下次 `importWithPreview` 返回 Failure。
     */
    fun setFailure(
        error: BackupError = BackupError.IMPORT_FAILED,
        message: String? = "fake importer failure",
    ) {
        nextResult = BackupResult.Failure(error, message)
    }
}