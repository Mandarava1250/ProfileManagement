package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 11 → 12：Phase 12「我」模块扩展 GPA 规则
 *
 * 给 `student_profile` 加 `gpaRuleJson`（TEXT，NOT NULL，默认 ''）：
 *  - 为避免引入 Room migration 与跨表耦合，规则以 JSON 字符串塞进单列
 *  - 空串 → 使用 [com.example.personalassistant.feature.me.domain.model.GpaRule.DEFAULT]（4.0 制默认转换表）
 *  - 非空 → kotlinx.serialization 反序列化为 [GpaRule]
 *  - 旧客户端读到的 gpaRuleJson = '' 时落回默认规则，**不抛异常、不丢历史绩点**
 *
 * ## 为什么 defaultValue = ''（而非 Room 默认 sentinel）
 *  - [StudentProfileEntity] 用 `@ColumnInfo(defaultValue = "''")` 显式声明 Room 期望 `''`
 *  - SQLite ALTER ADD COLUMN：写的就是 `TEXT DEFAULT ''`
 *  - 两边完全一致 → Room schema 校验通过，启动不再闪退
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
