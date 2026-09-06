package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 → v5：标签云（Phase 6c）
 *
 * 新增两张表 + 一列（颜色来源标识）：
 * 1. tags 表
 *    - id INTEGER PK AUTOINCREMENT
 *    - nameNormalized TEXT NOT NULL  -- 小写规范化名 + UNIQUE 索引
 *    - displayName TEXT NOT NULL     -- 原始大小写显示名
 *    - colorHex INTEGER NOT NULL     -- ARGB hex（不透明色）
 *    - colorSource TEXT NOT NULL DEFAULT 'default'  -- 调色板来源标识
 *    - createdAt INTEGER NOT NULL
 * 2. task_tag_cross_ref 表（junction）
 *    - taskId INTEGER NOT NULL
 *    - tagId INTEGER NOT NULL
 *    - PRIMARY KEY (taskId, tagId)
 *    - FOREIGN KEY (taskId) → tasks(id) ON DELETE CASCADE
 *    - FOREIGN KEY (tagId) → tags(id) ON DELETE CASCADE
 *
 * 历史 v4 数据无 tag 关联，新表初始为空。删除任务时通过 FK CASCADE 自动清空其关联行。
 * 删除标签时同理（plan v3.3 决策：同时清关联 + 标签本身）。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ===== tags 表 =====
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tags` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `nameNormalized` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `colorHex` INTEGER NOT NULL,
                `colorSource` TEXT NOT NULL DEFAULT 'default',
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_nameNormalized` ON `tags` (`nameNormalized`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tags_colorSource` ON `tags` (`colorSource`)"
        )

        // ===== task_tag_cross_ref 表 =====
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `task_tag_cross_ref` (
                `taskId` INTEGER NOT NULL,
                `tagId` INTEGER NOT NULL,
                PRIMARY KEY (`taskId`, `tagId`),
                FOREIGN KEY (`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY (`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_task_tag_cross_ref_tagId` ON `task_tag_cross_ref` (`tagId`)"
        )
    }
}