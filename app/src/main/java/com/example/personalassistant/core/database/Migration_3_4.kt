package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 → v4：tasks 表新增 manualOrder 列（Phase 6a 拖拽排序）
 *
 * - manualOrder: INTEGER，可空
 *   - null = 未手动排序，沿用现有 [TaskSort] 行为（优先级 / 截止日期 / ...）
 *   - 非 null = 用户拖拽后的排序权重，升序展示（值越小越靠前）
 *
 * 历史 v3 数据默认 manualOrder=null，沿用原排序语义，行为不变。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ===== tasks: 新增 manualOrder =====
        database.execSQL(
            "ALTER TABLE tasks ADD COLUMN manualOrder INTEGER DEFAULT NULL"
        )
    }
}
