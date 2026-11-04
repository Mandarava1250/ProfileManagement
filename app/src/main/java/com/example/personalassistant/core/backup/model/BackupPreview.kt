package com.example.personalassistant.core.backup.model

/**
 * Phase 8 Sub-step 4'（plan 8f'）：备份预览数据集合
 *
 * 由 [com.example.personalassistant.core.backup.diff.BackupDiffCalculator.compute] 产出：
 * 拿「备份 payload」与「当前 DB snapshot」做差异，得到 13 张表的 [TableDiff]。
 *
 * - [formatVersion] / [sourceDbVersion] / [createdAt] 直接来自备份 manifest，只读
 * - [perTable] 13 项，按 [BackupManifest.DEFAULT_TABLES] 顺序（外键依赖顺序）排好
 *
 * 用户在 [BackupPreviewScreen] 上修改的策略写在 [com.example.personalassistant.core.backup.model.TableDiff.strategy]
 * 字段里；调用 `BackupPreviewViewModel.confirmImport()` 时整份 [BackupPreview] 喂给
 * [com.example.personalassistant.core.backup.importer.BackupImporter.importWithPreview]。
 *
 * ## 不变量
 * - `perTable.size` == 表集合的表数（批次 C 起是 **18** 张，与
 *   [BackupManifest.DEFAULT_TABLES] 一致）
 * - 每项的 `tableName` 都来自 [BackupManifest.DEFAULT_TABLES]（不出现未知表）
 */
data class BackupPreview(
    val formatVersion: Int,
    val sourceDbVersion: Int,
    val createdAt: Long,
    val perTable: List<TableDiff<*>>,
) {
    /** 总新增行数（所有表 toAdd.size 之和） */
    val totalToAdd: Int get() = perTable.sumOf { it.toAdd.size }

    /** 总覆盖行数（所有表 toOverwrite.size 之和） */
    val totalToOverwrite: Int get() = perTable.sumOf { it.toOverwrite.size }

    /** 被 SKIP 策略（或外键级联 SKIP）跳过的表数 */
    val totalSkippedTables: Int
        get() = perTable.count {
            it.strategy == ImportStrategy.SKIP || it.skippedByForeignKey
        }

    /** 因外键被自动跳过的表数（用于 UI 灰色 chip 统计） */
    val totalSkippedByForeignKey: Int
        get() = perTable.count { it.skippedByForeignKey }
}