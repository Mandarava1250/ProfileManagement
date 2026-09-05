package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 迁移
 *
 * 变更内容：
 * 1. sub_tasks.parentTaskId: NOT NULL → NULL
 *    原因：支持无 Task 的纯清单模式（独立清单项）。
 * 2. sub_tasks 外键 onDelete: CASCADE → SET_NULL
 *    原因：任务删除时让子清单自动转为独立项，而非一并删除（避免数据丢失）。
 *
 * 由于 SQLite 在 ALTER TABLE 中不能修改外键 / 也不能直接改列的可空性，
 * 走 Room 官方推荐的「建新表 → 拷贝数据 → 删旧表 → 改名」安全路径。
 *
 * 关联索引一并重建：[parentTaskId] 单列索引 + [parentTaskId, order] 复合索引。
 */
val Migration_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ===== sub_tasks =====
        // 1) 建临时新表（结构对齐 v2）
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sub_tasks_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `parentTaskId` INTEGER,
                `title` TEXT NOT NULL,
                `isCompleted` INTEGER NOT NULL,
                `order` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        // 2) 拷贝数据。旧表的 parentTaskId 是 NOT NULL，但仍可能有值，
        //    这里直接整体拷过去即可（NULL 在新 schema 里也合法）。
        database.execSQL(
            """
            INSERT INTO `sub_tasks_new` (`id`, `parentTaskId`, `title`, `isCompleted`, `order`, `createdAt`)
            SELECT `id`, `parentTaskId`, `title`, `isCompleted`, `order`, `createdAt` FROM `sub_tasks`
            """.trimIndent()
        )
        // 3) 删旧表
        database.execSQL("DROP TABLE `sub_tasks`")
        // 4) 改名
        database.execSQL("ALTER TABLE `sub_tasks_new` RENAME TO `sub_tasks`")
        // 5) 重建索引
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_sub_tasks_parentTaskId` ON `sub_tasks` (`parentTaskId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_sub_tasks_parentTaskId_order` ON `sub_tasks` (`parentTaskId`, `order`)")
    }
}
