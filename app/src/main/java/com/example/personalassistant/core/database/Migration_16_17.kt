package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 16 → 17：Phase 23 习惯打卡
 *
 * ## 目标
 *
 * 新建两张表：
 * 1. `habits` —— 习惯定义（名称 / 图标 / 颜色 / 目标类型 / 目标次数 / 单位 / 顺序 / 归档）
 * 2. `habit_checkins` —— 打卡记录（习惯 + 日期 + 次数 + 备注）
 *
 * ## 为什么是「两张新表」而不是「往 tasks 上加两列」
 *
 * 习惯打卡和任务虽然都叫「要做的事」，但语义完全不同：
 * - 任务是一次性的、有截止日期的、做完就消失
 * - 习惯是**长期**的、按天重复的，还要保留历史打卡记录做统计
 *
 * 塞进 `tasks` 会让「今天该做什么」和「我这周坚持了几天」两种查询互相打架。
 *
 * ## 表形状必须与 Room 生成的 DDL 逐字一致
 *
 * Room 在编译期生成 `17.json`（schema 快照），运行期靠 `TableInfo` 比对
 * 「迁移出来的表」与「Entity 声明的表」是否一致 —— 不一致就抛
 * `Migration didn't properly handle: habits`。
 *
 * 因此下面的 DDL 要点：
 * - `id` 用 `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`（对应 `autoGenerate = true`）
 * - 所有列名与 `HabitEntity` / `HabitCheckInEntity` 的 `@ColumnInfo(name = ...)` 逐字一致
 * - 索引名用 Room 的命名规则 `index_<表名>_<列名>`
 * - 外键 `ON UPDATE NO ACTION ON DELETE CASCADE`（删习惯连带清打卡记录）
 *
 * ## 幂等
 *
 * 表 / 索引全部 `CREATE ... IF NOT EXISTS` —— 重复跑迁移不会炸。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        migration16To17Statements().forEach { database.execSQL(it) }
    }
}

/** 习惯表名 */
internal const val HABITS_TABLE = "habits"

/** 打卡记录表名 */
internal const val HABIT_CHECKINS_TABLE = "habit_checkins"

/**
 * 迁移语句序列（抽成函数供 JVM 单测逐条断言 —— 沿用 [Migration_12_13] 的做法）。
 */
internal fun migration16To17Statements(): List<String> = buildList {
    add(createHabitsTableSql())
    add(createHabitCheckInsTableSql())

    add("CREATE INDEX IF NOT EXISTS `index_habits_sortOrder` ON `$HABITS_TABLE` (`sortOrder`)")
    add("CREATE INDEX IF NOT EXISTS `index_habits_isArchived` ON `$HABITS_TABLE` (`isArchived`)")
    add(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_checkins_habitId_date` " +
            "ON `$HABIT_CHECKINS_TABLE` (`habitId`, `date`)",
    )
    add("CREATE INDEX IF NOT EXISTS `index_habit_checkins_date` ON `$HABIT_CHECKINS_TABLE` (`date`)")
}

/**
 * v17 的 `habits` 目标表结构（**必须与 Room 生成的形状逐字一致**）。
 *
 * Room 会把 `Long` 映射成 `INTEGER`、`Boolean` 映射成 `INTEGER NOT NULL`、
 * `String` 映射成 `TEXT NOT NULL`。
 */
internal fun createHabitsTableSql(): String = """
    CREATE TABLE IF NOT EXISTS `$HABITS_TABLE` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `name` TEXT NOT NULL,
        `iconKey` TEXT NOT NULL,
        `color` INTEGER NOT NULL,
        `targetType` TEXT NOT NULL,
        `targetCount` INTEGER NOT NULL,
        `unit` TEXT,
        `sortOrder` INTEGER NOT NULL,
        `isArchived` INTEGER NOT NULL,
        `createdAt` INTEGER NOT NULL
    )
""".trimIndent()

/**
 * v17 的 `habit_checkins` 目标表结构（**必须与 Room 生成的形状逐字一致**）。
 *
 * `(habitId, date)` 的唯一性由**唯一索引**保证，不靠表约束 ——
 * Room 的 `Index(value = [...], unique = true)` 生成的就是唯一索引。
 */
internal fun createHabitCheckInsTableSql(): String = """
    CREATE TABLE IF NOT EXISTS `$HABIT_CHECKINS_TABLE` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `habitId` INTEGER NOT NULL,
        `date` INTEGER NOT NULL,
        `count` INTEGER NOT NULL,
        `note` TEXT,
        `createdAt` INTEGER NOT NULL,
        FOREIGN KEY(`habitId`) REFERENCES `$HABITS_TABLE`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
    )
""".trimIndent()
