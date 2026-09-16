package com.example.personalassistant.core.backup.importer

import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupPreview

/**
 * Phase 10 10a-1（重构为接口）：备份导入器最小契约接口
 *
 * 把 [BackupImporter] 从具体类抽出接口，让 VM 通过接口注入，方便 JVM 单测写 fake。
 *
 * ## 故意只暴露 `importWithPreview`
 * - Phase 8f' 之后 [BackupImporter] 有两个入口：`import(InputStream)`（整表替换 / 快速替换）和
 *   `importWithPreview(BackupPayload, BackupPreview)`（合并模式 / 三档选择）。
 * - 当前 VM（[com.example.personalassistant.feature.settings.backup.BackupPreviewViewModel]）
 *   **只** 用 `importWithPreview`；`import` 是「快速替换」按钮的底层路径，由
 *   `SettingsViewModel` 调（间接通过 SAF Uri）。
 * - 接口只暴露 VM 用到的方法，让 Fake 实现更轻、契约更清晰。
 * - 后续若 VM 也要快速替换入口，再加方法即可。
 *
 * ## 与原 open class 的关系
 * - [BackupImporter] 仍是 `@Singleton`，实现这个接口
 * - Hilt 模块 [com.example.personalassistant.core.backup.di.BackupModule] 用 `@Binds` 绑接口到实现
 * - androidTest 的 [com.example.personalassistant.core.backup.importer.BackupImporterTest]
 *   直接 `new BackupImporter(...)`，**未受本次接口化影响**
 */
interface IBackupImporter {
    /**
     * 合并模式：按 [BackupPreview] 中每张表指定的 [com.example.personalassistant.core.backup.model.ImportStrategy]
     * 合并写入 Room。任一行失败 → 全部回滚（事务原子）。
     */
    suspend fun importWithPreview(
        payload: BackupPayload,
        preview: BackupPreview,
    ): com.example.personalassistant.core.backup.model.BackupResult<BackupPayload>
}