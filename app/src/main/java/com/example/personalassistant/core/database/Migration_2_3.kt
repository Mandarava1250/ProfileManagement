package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3：tasks 表新增 repeatDays 列（Phase 4b）
 *
 * - repeatDays: TEXT，可空
 *   - null = 无自定义星期（按 repeatType 解读）
 *   - 非 null = 逗号分隔的 DayOfWeek.name 序列，例如 "MONDAY,WEDNESDAY,FRIDAY"
 *
 * 历史 v2 数据默认 repeatDays=null，沿用原 repeatType 语义，行为不变。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ===== tasks: 新增 repeatDays =====
        database.execSQL(
            "ALTER TABLE tasks ADD COLUMN repeatDays TEXT DEFAULT NULL"
        )
    }
}
