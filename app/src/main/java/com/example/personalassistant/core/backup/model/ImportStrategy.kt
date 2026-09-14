package com.example.personalassistant.core.backup.model

/**
 * Phase 8 Sub-step 4'（plan 8f'）：备份导入「每表策略」枚举
 *
 * 每张表在导入时按所选策略执行：
 * - [ADD]：仅写入备份中存在、当前库中不存在的行（同主键视为重复 → 跳过）
 * - [OVERWRITE]：同主键的行用备份值覆盖（按主键 UPDATE / REPLACE 语义）
 * - [SKIP]：整张表跳过，不写任何行
 *
 * 决定写在 [com.example.personalassistant.core.backup.model.TableDiff.strategy] 中，
 * 用户在 BackupPreviewScreen 中可逐表切换；快捷按钮会把所有表置为同一策略。
 *
 * ## 与整表替换的关系
 * [SKIP] 与「快速替换」模式的整表清空+整表写入是对立的——
 * 前者是「保持现状，可能增加新行」，后者是「先全部干掉再重写」。
 * 「快速替换」入口（[com.example.personalassistant.core.backup.importer.BackupImporter.import]）
 * 不读 [Config]，等价于对每张表都执行 OVERWRITE 且允许改主键（旧行为）。
 */
enum class ImportStrategy {
    ADD,
    OVERWRITE,
    SKIP,
}