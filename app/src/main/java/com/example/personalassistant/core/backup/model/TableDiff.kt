package com.example.personalassistant.core.backup.model

/**
 * Phase 8 Sub-step 4'（plan 8f'）：单张表的导入差异结果
 *
 * 给 UI 展示：每张表要新增多少行、要覆盖多少行；用户可以在 [strategy] 上切换 ADD/OVERWRITE/SKIP。
 *
 * ## 类型擦除说明
 * Kotlin data class 的泛型只在编译期保留；运行时擦成 `TableDiff<*>`。
 * UI 渲染样本行时，按 [tableName] 字符串判断显示哪些字段。
 *
 * ## `skippedByForeignKey` 的语义
 * true 表示这张表被上游表的 SKIP 策略「拖累」自动跳过了 —— UI 用灰色 chip 提示。
 * 它**不是**用户选的策略，而是 [BackupDiffCalculator] 计算时根据外键拓扑推断出来的。
 *
 * @param tableName 表名（与 [BackupManifest.DEFAULT_TABLES] 中的字符串一一对应）
 * @param strategy 当前选中策略（用户在 PreviewScreen 中切换；importWithPreview 用它决定写入行为）
 * @param toAdd 备份中存在但当前库中不存在的行 → INSERT
 * @param toOverwrite 备份行主键在当前库中已存在 → UPDATE（OVERWRITE 模式）或忽略（ADD 模式）
 * @param skippedByForeignKey true = 因上游表被 SKIP，本表自动跳过（用户不能再切换）
 * @param sampleRowIds 仅展示用：前 5 行 id 样本（便于 UI 摘要），不参与导入逻辑
 */
data class TableDiff<T>(
    val tableName: String,
    val strategy: ImportStrategy,
    val toAdd: List<T>,
    val toOverwrite: List<T>,
    val skippedByForeignKey: Boolean = false,
    val sampleRowIds: List<Long> = emptyList(),
)