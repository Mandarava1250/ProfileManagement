package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 11 → 12：Phase 12「我」模块扩展 GPA 规则
 *
 * 给 `student_profile` 加 `gpaRuleJson`（TEXT，nullable，默认 ''）：
 *  - 为避免引入 Room migration 与跨表耦合，规则以 JSON 字符串塞进单列
 *  - 空串 → 使用 [com.example.personalassistant.feature.me.domain.model.GpaRule.DEFAULT]（4.0 制默认转换表）
 *  - 非空 → kotlinx.serialization 反序列化为 [GpaRule]
 *  - 旧客户端读到的 gpaRuleJson = '' 时落回默认规则，**不抛异常、不丢历史绩点**
 *
 * ## 为什么是 nullable + DEFAULT ''
 *  - 与 [StudentProfileEntity] 的 `gpaRuleJson: String = ""` 对齐（Room schema 一致性要求）
 *  - SQLite ALTER ADD COLUMN 不支持 `NOT NULL DEFAULT ''` 之外的形式（nullability 必须靠"列类型"而非约束）
 *  - 实测：`TEXT DEFAULT ''` 行为与 `TEXT NULL DEFAULT NULL` 一致地允许旧行 NULL 出现，
 *    mapper 层用 `json.isBlank() → DEFAULT` 兜底，正反都对得上
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MIGRATION_11_12_STATEMENTS.forEach { database.execSQL(it) }
    }
}

internal val MIGRATION_11_12_STATEMENTS: List<String> = listOf(
    // ① 「我」档案表加 GPA 规则 JSON 列
    "ALTER TABLE `student_profile` ADD COLUMN `gpaRuleJson` TEXT DEFAULT ''",
)
