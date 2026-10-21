package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 11 → 12：Phase 12「我」模块扩展 GPA 规则
 *
 * 给 `student_profile` 加 `gpaRuleJson`（TEXT，**nullable**，默认 ''）：
 *  - 为避免引入 Room migration 与跨表耦合，规则以 JSON 字符串塞进单列
 *  - 空串 → 使用 [com.example.personalassistant.feature.me.domain.model.GpaRule.DEFAULT]（4.0 制默认转换表）
 *  - 非空 → kotlinx.serialization 反序列化为 [GpaRule]
 *  - 旧客户端读到的 gpaRuleJson = '' 时落回默认规则，**不抛异常、不丢历史绩点**
 *
 * ## ⚠️ ADD COLUMN 出来的列天生是 nullable
 *
 * SQLite 的 `ALTER TABLE ... ADD COLUMN` **不会**给新列附带 NOT NULL 约束，
 * 所以 `gpaRuleJson` 这一列在物理表里是 `notNull=false`。
 * Entity 侧必须写 `val gpaRuleJson: String?` + `@ColumnInfo(defaultValue = "''")`
 * 才能同时对上 notNull 与 defaultValue 两项 —— `defaultValue` 调不了 notNull。
 *
 * （`DEFAULT ''` 与 `NOT NULL DEFAULT ''` 都能加成功，但前者允许存量行为 NULL；
 *  存量行本来就是新列填的 DEFAULT ''，实际上不会出现 NULL。）
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
