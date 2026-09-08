package com.example.personalassistant.core.database

import android.content.ContentValues
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.personalassistant.feature.calendar.data.seed.DefaultEventCategories

/**
 * v6 → v7：日历事件分类（Phase 8 Sub-step 9）
 *
 * ## schema 变更
 *
 * ### 1. 新增一张表：event_categories
 * - id INTEGER PK AUTOINCREMENT
 * - name TEXT NOT NULL
 * - color INTEGER NOT NULL
 * - iconKey TEXT NOT NULL DEFAULT 'Label'
 * - sortOrder INTEGER NOT NULL DEFAULT 0
 * - isDefault INTEGER NOT NULL DEFAULT 0
 *
 * ### 2. events 表新增一列：categoryId
 * - categoryId INTEGER（外键 reference event_categories.id ON DELETE SET NULL，可空）
 * - 索引：events_index_categoryId
 *
 * ### 3. 旧 category 字符串 → categoryId 映射
 * - 旧值"工作"→id(工作分类)
 * - 旧值"学习"→id(学习分类)
 * - 旧值"生活"→id(生活分类)
 * - 旧值"健康"→id(健康分类)
 * - 旧值"财务"→id(财务分类)
 * - 旧值"社交"→id(社交分类)
 * - 旧值"生日"→id(生日分类)
 * - 其他 / 空 → NULL
 *
 * 历史 v6 events 表的 category 字段保留（只读兼容），不删除。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ===== 1. 新增 event_categories 表 =====
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `event_categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `color` INTEGER NOT NULL,
                `iconKey` TEXT NOT NULL DEFAULT 'Label',
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `isDefault` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_event_categories_sortOrder` " +
                "ON `event_categories` (`sortOrder`)"
        )

        // ===== 2. 写入预置分类 =====
        // 用 ContentValues 写，主键由 SQLite 自动分配
        DefaultEventCategories.build().forEach { entity ->
            val cv = ContentValues().apply {
                put("name", entity.name)
                put("color", entity.color)
                put("iconKey", entity.iconKey)
                put("sortOrder", entity.sortOrder)
                // isDefault 在 SQLite 没有 Boolean，存 INTEGER（0/1）
                put("isDefault", if (entity.isDefault) 1 else 0)
            }
            database.insert("event_categories", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, cv)
        }

        // ===== 3. events 表新增 categoryId 列 =====
        database.execSQL(
            "ALTER TABLE `events` ADD COLUMN `categoryId` INTEGER DEFAULT NULL"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_events_categoryId` ON `events` (`categoryId`)"
        )

        // ===== 4. 旧 category 字符串 → categoryId 映射 =====
        // 先用 id=0 临时占位（避免 UNIQUE 冲突），最后把 0 视为 null
        // 因为 sub-step 9 之前的 category 字段没有外键约束，所以只 update
        val nameToIdMap = mutableMapOf<String, Long>()
        // 读出预置分类的 id（按 name 索引）
        database.query("SELECT id, name FROM event_categories").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1)
                nameToIdMap[name] = id
            }
        }
        // 把 events.category 映射到 categoryId
        nameToIdMap.forEach { (name, id) ->
            database.execSQL(
                "UPDATE `events` SET `categoryId` = ? WHERE `category` = ? AND `categoryId` IS NULL",
                arrayOf<Any>(id, name)
            )
        }
        // events.category 字段保留（只读兼容），不删除
    }
}
