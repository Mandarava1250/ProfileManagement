package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6：节假日表（Phase 8 Sub-step 7）
 *
 * ## schema 变更
 *
 * ### 1. 新增一张表：holidays
 * - id INTEGER PK AUTOINCREMENT
 * - dateEpochDay INTEGER NOT NULL   -- 自 1970-01-01 起的天数（LocalDate.toEpochDay）
 * - name TEXT NOT NULL              -- "国庆节" / "春节"
 * - type TEXT NOT NULL              -- HolidayType.name
 * - year INTEGER NOT NULL           -- 冗余字段，便于按年扫描
 * - note TEXT                       -- 可选备注（"农历正月初一" / "调休上班"）
 *
 * ### 2. tasks 表新增一列
 * - holidayStrategy TEXT NOT NULL DEFAULT 'SKIP'  -- HolidayStrategy.name
 *
 * ## 索引
 * - holidays: UNIQUE(dateEpochDay, type) + (year)
 *
 * 历史 v5 数据无节假日关联，holidays 表初始为空；tasks 表新增列使用默认值兼容。
 * 预置数据通过 [DatabaseCallback.seedHolidays] 在 onCreate 时 seed（仅首次安装）。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ===== holidays 表 =====
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `holidays` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `dateEpochDay` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `year` INTEGER NOT NULL,
                `note` TEXT
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_holidays_dateEpochDay_type` " +
                "ON `holidays` (`dateEpochDay`, `type`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_holidays_year` ON `holidays` (`year`)"
        )

        // ===== tasks 表新增 holidayStrategy 列 =====
        database.execSQL(
            "ALTER TABLE `tasks` ADD COLUMN `holidayStrategy` TEXT NOT NULL DEFAULT 'SKIP'"
        )
    }
}