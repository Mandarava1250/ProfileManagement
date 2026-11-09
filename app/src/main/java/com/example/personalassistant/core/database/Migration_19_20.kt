package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 19 → 20：课表「一门课多课时」拆表（Phase 28 · P1 数据层）
 *
 * ## 目标
 *
 * 1. 新建一张表 `course_sessions`（课时：一门课的一个上课时段）
 * 2. 把存量 `courses` 每行的主时段播种成一条课时（存量数据零损失）
 *
 * ## 为什么要拆
 *
 * 改造前：一门课 = 一个上课时段（星期几 + 起止时间都长在 `courses` 行上）。
 * 改造后：一门课 = `courses` 一行（课程本体）+ `course_sessions` N 行（课时）。
 * 例：高等数学每周一 1-2 节、周三 3-4 节、周五 5-6 节 → courses 1 行 + sessions 3 行。
 *
 * ## ⚠️ courses 表**不动**
 *
 * 本次迁移**不 ALTER、不重建、不删列** `courses` 表。
 * 它的 `dayOfWeek` / `startTime` / `endTime` 三列保留，语义收窄为
 * 「课程主时段（= 第一个课时）的冗余镜像」，由 Repository 单点维护。
 * 理由：SQLite 删列要重建表，风险高；存量数据零损失；老备份导入有落点。
 *
 * ## ⚠️ 新表形状必须与 Room 生成的 DDL 逐字一致
 *
 * Room 在编译期生成 `20.json`（schema 快照），运行期靠 `TableInfo` 比对
 * 「迁移出来的表」与「Entity 声明的表」——不一致就抛
 * `Migration didn't properly handle: course_sessions`。
 *
 * 要点：
 * - `id` 用 `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`（对应 `autoGenerate = true`）
 * - Int → `INTEGER NOT NULL`；Long → `INTEGER NOT NULL`
 * - ⚠️ **任何一列都不要写 DEFAULT** —— 实体上没有 `@ColumnInfo(defaultValue = ...)`，
 *   Room 生成的 20.json 里就没有 DEFAULT，两边必须一致
 * - 索引名用 Room 的命名规则 `index_course_sessions_<cols>`
 * - 外键 `ON UPDATE NO ACTION ON DELETE CASCADE`（删课连带清课时）
 *
 * ## 播种为什么用普通 INSERT 而不是 INSERT OR IGNORE
 *
 * 播种是「一次性把 courses 的每一行搬成一条课时」，courses 的 id 是唯一的，
 * 所以 `(courseId, sortOrder)` 不会撞 —— 普通 INSERT 足够。
 * 真要重复执行迁移，靠的是「建表 / 建索引带 IF NOT EXISTS」这一层幂等，
 * 迁移本身在 SQLite 里只跑一次。
 *
 * ## 幂等
 *
 * 建表 / 建索引用 `CREATE ... IF NOT EXISTS`；`ALTER TABLE ADD COLUMN`
 * 在 SQLite 里没有 `IF NOT EXISTS` 语法 —— 迁移本来就只跑一次，不重复执行。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        migration19To20Statements().forEach { database.execSQL(it) }
    }
}

/** 课时表名 */
internal const val COURSE_SESSIONS_TABLE = "course_sessions"

/** 课程表名（课时的外键父表） */
internal const val COURSES_TABLE = "courses"

/**
 * 迁移语句序列（抽成函数供 JVM 单测逐条断言 —— 沿用 [MIGRATION_18_19] 的做法）。
 */
internal fun migration19To20Statements(): List<String> = buildList {
    add(createCourseSessionsTableSql())
    add(
        "CREATE INDEX IF NOT EXISTS `index_course_sessions_courseId` " +
            "ON `$COURSE_SESSIONS_TABLE` (`courseId`)",
    )
    add(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_course_sessions_courseId_sortOrder` " +
            "ON `$COURSE_SESSIONS_TABLE` (`courseId`, `sortOrder`)",
    )
    add(seedCourseSessionsFromCoursesSql())
}

/**
 * v20 的 `course_sessions` 目标表结构（**必须与 Room 生成的形状逐字一致**）。
 *
 * ⚠️ 任何一列都**不要**加 DEFAULT —— 实体上没有 `@ColumnInfo(defaultValue = ...)`，
 * Room 生成的 20.json 里也没有 DEFAULT。多写一个 DEFAULT 就是运行期闪退。
 */
internal fun createCourseSessionsTableSql(): String = """
    CREATE TABLE IF NOT EXISTS `$COURSE_SESSIONS_TABLE` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `courseId` INTEGER NOT NULL,
        `sortOrder` INTEGER NOT NULL,
        `dayOfWeek` INTEGER NOT NULL,
        `startTime` INTEGER NOT NULL,
        `endTime` INTEGER NOT NULL,
        FOREIGN KEY(`courseId`) REFERENCES `$COURSES_TABLE`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
    )
""".trimIndent()

/**
 * 播种：把 `courses` 每行的主时段搬成一条课时（sortOrder = 0）。
 *
 * 存量课程迁移后变成「只有一个课时的课」—— 行为与改造前完全一致。
 *
 * ⚠️ 这里刻意**不写反引号**：SQLite 两种写法等价，但不带反引号的字面量
 * 便于 JVM 单测直接 `contains("INSERT INTO course_sessions")` 断言。
 */
internal fun seedCourseSessionsFromCoursesSql(): String =
    "INSERT INTO course_sessions (courseId, sortOrder, dayOfWeek, startTime, endTime) " +
        "SELECT id, 0, dayOfWeek, startTime, endTime FROM courses"
