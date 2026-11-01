package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 11 → 12：Phase 12「我」模块扩展 GPA 规则
 *
 * ## 目标：`student_profile` 的物理形状必须与 v12 完全一致
 *
 * v11 的**真实快照**（`app/schemas/.../11.json`）里 `student_profile` 是 Room 自己生成的形状：
 *
 * ```sql
 * CREATE TABLE `student_profile` (
 *   `accountId`   INTEGER NOT NULL,
 *   `gpaTarget`   REAL NOT NULL,        -- 无 DEFAULT
 *   `resumeText`  TEXT NOT NULL,        -- 无 DEFAULT
 *   `gpaRuleJson` TEXT NOT NULL,        -- 无 DEFAULT，且是 NOT NULL
 *   `updatedAt`   INTEGER NOT NULL,
 *   PRIMARY KEY(`accountId`)
 * )
 * ```
 *
 * 而 v12（`12.json`）要求：
 *
 * ```sql
 * CREATE TABLE `student_profile` (
 *   `accountId`   INTEGER NOT NULL,
 *   `gpaTarget`   REAL NOT NULL DEFAULT 4.0,
 *   `resumeText`  TEXT NOT NULL DEFAULT '',
 *   `gpaRuleJson` TEXT DEFAULT '',      -- nullable + DEFAULT ''
 *   `updatedAt`   INTEGER NOT NULL,
 *   PRIMARY KEY(`accountId`)
 * )
 * ```
 *
 * ## ⚠️ 为什么必须重建表，不能 ADD COLUMN（这是本轮修的真 bug）
 *
 * 老写法只有一句 `ALTER TABLE student_profile ADD COLUMN gpaRuleJson TEXT DEFAULT ''`，
 * 在 **v11 快照基线上直接炸**：
 *
 * ```
 * android.database.sqlite.SQLiteException: duplicate column name: gpaRuleJson
 * ```
 *
 * 因为 v11 的表里**已经有** `gpaRuleJson` 这一列（11.json 可见），再 ADD 一次必然重名。
 * —— 也就是说，`Migration_11_12` 早该加的列在 v11 就已经存在了。
 *
 * 但「加一列」本身也不是这次 v12 的正确解法：11.json → 12.json 的**全部**差异是
 * 三列的 `notNull` / `defaultValue`：
 *
 * | 列 | v11 | v12 |
 * |---|---|---|
 * | gpaTarget | NOT NULL，无 DEFAULT | NOT NULL，DEFAULT 4.0 |
 * | resumeText | NOT NULL，无 DEFAULT | NOT NULL，DEFAULT '' |
 * | gpaRuleJson | NOT NULL，无 DEFAULT | nullable，DEFAULT '' |
 *
 * SQLite 的 `ADD COLUMN` **改不了已有列**的 NOT NULL 与 DEFAULT，
 * 所以正解只有一条路：**建新表 → 搬数据 → 删旧表 → 改名**（与 [Migration_8_9] 同一套路）。
 * 光「跳过 ADD」也不行 —— 那样三列的 notNull / defaultValue 全对不上，
 * Room 校验时会抛 `Migration didn't properly handle: student_profile`。
 *
 * ## 为什么这次能同时兜住两种 v11 来源
 *
 * v11 的 `student_profile` 有两种可能的物理形状：
 *
 * - **A**：Room 自己建的（= 11.json）：**有** `gpaRuleJson`，但 NOT NULL 且无 DEFAULT；
 * - **B**：[Migration_10_11] 手写 CREATE TABLE 建的：`gpaTarget` / `resumeText` 带 DEFAULT，
 *   但**没有** `gpaRuleJson` 这一列。
 *
 * 本迁移用 `PRAGMA table_info` 判源表有没有这一列：
 *
 * - 源表**有** `gpaRuleJson` → 搬完数据再把用户已存的 GPA 规则 JSON 拷进新列（不丢配置）；
 * - 源表**没有** → 新列直接吃 `DEFAULT ''`（等价于 [Migration_10_11] 旧行为）。
 *
 * 两种来源最终都收敛到同一张 v12 形状的表。
 *
 * ## 幂等
 *
 * 重建表本身就是幂等的：临时表先 `DROP TABLE IF EXISTS` 清干净，
 * 对已经是 v12 的库再跑一次，形状与数据都不变。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // v11 库里压根没有这张表（理论上不可能发生）：直接按 v12 形状建出来
        if (!tableExists(database, STUDENT_PROFILE_TABLE)) {
            database.execSQL(createStudentProfileTableSql(STUDENT_PROFILE_TABLE))
            return
        }
        // 源表有没有 gpaRuleJson？两种 v11 来源形状不同（见类注释）
        val sourceHasGpaRuleJson = hasColumn(database, STUDENT_PROFILE_TABLE, GPA_RULE_JSON_COLUMN)
        migration11To12Statements(sourceHasGpaRuleJson).forEach { database.execSQL(it) }
    }
}

/** 「我」档案表名 */
internal const val STUDENT_PROFILE_TABLE = "student_profile"

/** 重建过程中的临时表名（重建完会被改名成 [STUDENT_PROFILE_TABLE]） */
internal const val STUDENT_PROFILE_TEMP_TABLE = "student_profile_v12"

/** GPA 规则 JSON 列名 */
internal const val GPA_RULE_JSON_COLUMN = "gpaRuleJson"

/**
 * v12 的目标表结构（**必须与 `12.json` 的 Room 形状逐字一致**）。
 *
 * DEFAULT 的写法尤其要逐字对齐：写成 `DEFAULT '4.0'` 会与 Room 期望的 `defaultValue = "4.0"`
 * 对不上，而这种错编译期完全看不出来，只在迁移真跑时才炸（见 [Migration_9_10] 的同类说明）。
 */
internal fun createStudentProfileTableSql(tableName: String): String = """
    CREATE TABLE IF NOT EXISTS `$tableName` (
        `accountId` INTEGER NOT NULL,
        `gpaTarget` REAL NOT NULL DEFAULT 4.0,
        `resumeText` TEXT NOT NULL DEFAULT '',
        `gpaRuleJson` TEXT DEFAULT '',
        `updatedAt` INTEGER NOT NULL,
        PRIMARY KEY(`accountId`)
    )
""".trimIndent()

/**
 * 迁移语句序列（按序执行，抽成函数供 JVM 单测逐条断言 —— 沿用 [MIGRATION_10_11_STATEMENTS] 的做法）。
 *
 * @param sourceHasGpaRuleJsonColumn 源表是否已有 `gpaRuleJson` 列：
 *   true = v11 由 Room 自己建的库（11.json 形状）；false = v10→v11 升级上来的库（[Migration_10_11] 形状）。
 *
 * 顺序有依赖，不要重排：
 * ① 清临时表 → ② 建 v12 形状的新表 → ③ 搬 4 列公共数据
 * → ④（仅当源表有 gpaRuleJson）搬 GPA 规则 JSON → ⑤ 删旧表 → ⑥ 改名
 */
internal fun migration11To12Statements(sourceHasGpaRuleJsonColumn: Boolean): List<String> = buildList {
    // ① 先清干净临时表：重跑 / 迁移被中断后残留都能安全覆盖（幂等的关键）
    add("DROP TABLE IF EXISTS `${STUDENT_PROFILE_TEMP_TABLE}`")

    // ② 建 v12 形状的新表
    add(createStudentProfileTableSql(STUDENT_PROFILE_TEMP_TABLE))

    // ③ 搬数据：先只搬 4 列公共数据，gpaRuleJson 留给 ④（或直接吃新表的 DEFAULT ''）
    add(
        "INSERT OR REPLACE INTO `${STUDENT_PROFILE_TEMP_TABLE}` " +
            "(`accountId`, `gpaTarget`, `resumeText`, `updatedAt`) " +
            "SELECT `accountId`, COALESCE(`gpaTarget`, 4.0), COALESCE(`resumeText`, ''), `updatedAt` " +
            "FROM `${STUDENT_PROFILE_TABLE}`",
    )

    // ④ 源表本来就有 gpaRuleJson（v11 由 Room 建的库）→ 把用户已存的规则 JSON 搬过去，别丢配置
    if (sourceHasGpaRuleJsonColumn) {
        add(
            "UPDATE `${STUDENT_PROFILE_TEMP_TABLE}` SET `gpaRuleJson` = COALESCE(" +
                "(SELECT s.`gpaRuleJson` FROM `${STUDENT_PROFILE_TABLE}` s " +
                "WHERE s.`accountId` = `${STUDENT_PROFILE_TEMP_TABLE}`.`accountId`), '')",
        )
    }

    // ⑤ 删旧表
    add("DROP TABLE `${STUDENT_PROFILE_TABLE}`")

    // ⑥ 改名成正式表名
    add("ALTER TABLE `${STUDENT_PROFILE_TEMP_TABLE}` RENAME TO `${STUDENT_PROFILE_TABLE}`")
}

/**
 * 表是否存在（`sqlite_master` 只读查询，迁移事务内可用 —— 与 [Migration_8_9] 同款做法）。
 *
 * 用于兜住「v11 库里压根没有 student_profile 表」这种异常形状：
 * 此时直接按 v12 形状建出来，而不是让后面的 DROP / RENAME 报 no such table。
 */
private fun tableExists(database: SupportSQLiteDatabase, tableName: String): Boolean =
    database.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'",
    ).use { cursor -> cursor.count > 0 }

/**
 * 表里是否已有该列（`PRAGMA table_info` 只读检查，迁移事务内可用）。
 *
 * 这是本轮幂等的关键：v11 的 `student_profile` 有两种来源形状（见类注释），
 * `gpaRuleJson` 在其中一种里存在、另一种里不存在。
 */
private fun hasColumn(
    database: SupportSQLiteDatabase,
    tableName: String,
    columnName: String,
): Boolean = database.query("PRAGMA table_info(`${tableName}`)").use { cursor ->
    var found = false
    while (cursor.moveToNext()) {
        // PRAGMA table_info 的列序：cid / name / type / notnull / dflt_value / pk
        if (cursor.getString(1) == columnName) {
            found = true
            break
        }
    }
    found
}
