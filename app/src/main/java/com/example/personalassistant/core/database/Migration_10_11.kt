package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 10 → 11：Phase 12「我」模块
 *
 * 加 `courses.credit`（学分，REAL，nullable）和 `courses.gradePoint`（绩点 0.0-4.0，REAL，nullable）。
 * 同时建表 `student_profile`（「我」档案，主键 accountId）。
 *
 * ## 为什么用最朴素的 ALTER + CREATE
 * - credit/gradePoint 是 nullable + default NULL，对存量行零侵入
 * - student_profile 是独立表，不引用 courses / semesters（成绩由 [CalculateGpaUseCase] 现场聚合），
 *   不需要 FK，CREATE TABLE IF NOT EXISTS 就够
 * - SQLite 不支持 ADD COLUMN 带 NOT NULL DEFAULT NULL；写 NOT NULL DEFAULT 0 会把存量课程"算 0 学分"，
 *   与 [CalculateGpaUseCase] "缺则排除"语义冲突；nullable 是更诚实的选择
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MIGRATION_10_11_STATEMENTS.forEach { database.execSQL(it) }
    }
}

internal val MIGRATION_10_11_STATEMENTS: List<String> = listOf(
    // ===== ① 课程表加学分 / 绩点 =====
    "ALTER TABLE `courses` ADD COLUMN `credit` REAL DEFAULT NULL",
    "ALTER TABLE `courses` ADD COLUMN `gradePoint` REAL DEFAULT NULL",

    // ===== ② 「我」档案表 =====
    """
        CREATE TABLE IF NOT EXISTS `student_profile` (
            `accountId` INTEGER NOT NULL,
            `gpaTarget` REAL NOT NULL DEFAULT 4.0,
            `resumeText` TEXT NOT NULL DEFAULT '',
            `updatedAt` INTEGER NOT NULL,
            PRIMARY KEY(`accountId`)
        )
    """.trimIndent(),
)
