package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 12 → 13：Phase 15「任务 → 清单」模块扩展
 *
 * ## 目标：两件事
 *
 * 1. `sub_tasks` 加 5 列：分类 / 截止时间 / 优先级 / 金额 / 嵌套父指针
 * 2. 新建 `sub_task_tag_cross_ref`（清单项 ↔ 标签 关联表）
 *
 * ## 为什么这次可以放心用 ADD COLUMN（与 [Migration_11_12] 的教训对照）
 *
 * [Migration_11_12] 必须**重建表**，是因为它要改已有列的 NOT NULL / DEFAULT ——
 * SQLite 的 `ADD COLUMN` 改不了已有列。而本轮 5 列全是**新增列**，
 * `ALTER TABLE ... ADD COLUMN` 完全够用。
 *
 * 唯一有坑的是 `priority`：它是 NOT NULL 列，而 SQLite 规定
 * 「给已有行的表加 NOT NULL 列必须带非 NULL 的 DEFAULT」，所以必须写
 * `NOT NULL DEFAULT 1`，并且 Entity 侧要用 `@ColumnInfo(defaultValue = "1")` 声明 ——
 * Room 迁移校验会**逐列比对 defaultValue**，两边不一致就抛
 * `Migration didn't properly handle: sub_tasks`（线上闪退同款）。
 *
 * ## 自引用外键的 ADD COLUMN 限制
 *
 * `parentSubTaskId` 带 `REFERENCES sub_tasks(id)`。SQLite 规定：
 * 带 REFERENCES 的新列，其 DEFAULT 必须是 NULL（否则无法校验已有行）。
 * 本迁移里该列没有 DEFAULT（= NULL），满足限制 ✓
 *
 * ## 幂等
 *
 * - 列：用 `PRAGMA table_info` 判源表有没有这一列，有就跳过 ADD
 * - 表 / 索引：全部用 `CREATE ... IF NOT EXISTS`
 *
 * 对已经是 v13 的库再跑一次，形状与数据都不变。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_12_13: Migration = object : Migration(12, 13) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 源表在不在 / 新列已存在哪些（决定要发哪些语句）
        val existingColumns: Set<String>? = if (subTasksTableExists(database, SUB_TASKS_TABLE)) {
            SUB_TASKS_NEW_COLUMN_DDL.keys.filter { subTaskHasColumn(database, SUB_TASKS_TABLE, it) }.toSet()
        } else {
            null
        }
        val crossRefExists = subTasksTableExists(database, SUB_TASK_TAG_CROSS_REF_TABLE)
        migration12To13Statements(
            existingColumns = existingColumns,
            subTaskTagTableExists = crossRefExists,
        ).forEach { database.execSQL(it) }
    }
}

/** 清单项表名 */
internal const val SUB_TASKS_TABLE = "sub_tasks"

/** 清单项 ↔ 标签 关联表名 */
internal const val SUB_TASK_TAG_CROSS_REF_TABLE = "sub_task_tag_cross_ref"

/**
 * 老数据的默认优先级：中优先级。
 *
 * 取 `TaskPriority.MEDIUM.dbValue`（= 1，与 `TaskEntity.priority` 的默认值一致），
 * 语义上「没填优先级的清单项按中优先级处理」，与任务域保持同款口径。
 */
internal const val DEFAULT_SUB_TASK_PRIORITY = 1

/**
 * v13 新增的 5 列 → ADD COLUMN 语句片段。
 *
 * 键 = 列名（与 `SubTaskEntity` 的 `@ColumnInfo(name = ...)` 逐字一致），
 * 值 = 该列的 DDL 片段（不含 `ALTER TABLE ... ADD COLUMN` 前缀）。
 */
internal val SUB_TASKS_NEW_COLUMN_DDL: Map<String, String> = mapOf(
    // 分类名（TEXT，可空）
    "category" to "`category` TEXT",
    // 截止时间（INTEGER 毫秒时间戳，可空）
    "dueDate" to "`dueDate` INTEGER",
    // 优先级（INTEGER，NOT NULL + DEFAULT 1 = 中优先级）
    "priority" to "`priority` INTEGER NOT NULL DEFAULT $DEFAULT_SUB_TASK_PRIORITY",
    // 金额（REAL，可空）
    "amount" to "`amount` REAL",
    // 嵌套父指针（自引用 FK，默认 NULL —— SQLite 要求 REFERENCES 新列的 DEFAULT 必须是 NULL）
    "parentSubTaskId" to
        "`parentSubTaskId` INTEGER REFERENCES `$SUB_TASKS_TABLE`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
)

/** v13 新增列的顺序（迁移语句的发出顺序） */
internal val SUB_TASKS_NEW_COLUMNS: List<String> = SUB_TASKS_NEW_COLUMN_DDL.keys.toList()

/**
 * 迁移语句序列（按序执行，抽成函数供 JVM 单测逐条断言 —— 沿用 [Migration_11_12] 的做法）。
 *
 * @param existingColumns 源表**已有**的新列名集合；`null` 表示源表压根不存在（见下）。
 *   传空集合 = 干净的 v12 库 → 发出全部 5 条 ADD COLUMN。
 * @param subTaskTagTableExists 关联表是否已存在（已存在则跳过建表 + 建索引）。
 *
 * 顺序有依赖，不要重排：
 * ① （源表缺失时）按 v13 形状建出 `sub_tasks`
 * ② 逐列 ADD COLUMN（已存在的列跳过）
 * ③ 建 `parentSubTaskId` 索引
 * ④ 建 `sub_task_tag_cross_ref` 表
 * ⑤ 建 `sub_task_tag_cross_ref` 的 tagId 索引
 */
internal fun migration12To13Statements(
    existingColumns: Set<String>? = emptySet(),
    subTaskTagTableExists: Boolean = false,
): List<String> = buildList {
    if (existingColumns == null) {
        // v12 库里理论上不可能没有 sub_tasks（Room 一定会建）。
        // 真发生了就按 v13 形状直接建出来，别让后面的 ALTER 报 no such table。
        add(createSubTasksTableSql(SUB_TASKS_TABLE))
    } else {
        SUB_TASKS_NEW_COLUMN_DDL.forEach { (column, ddl) ->
            if (column !in existingColumns) {
                add("ALTER TABLE `$SUB_TASKS_TABLE` ADD COLUMN $ddl")
            }
        }
    }

    // 自引用外键的索引（两条分支都要建 —— Room 迁移校验会比对索引集合，缺一个就炸）
    add(
        "CREATE INDEX IF NOT EXISTS `index_sub_tasks_parentSubTaskId` " +
            "ON `$SUB_TASKS_TABLE` (`parentSubTaskId`)",
    )

    if (!subTaskTagTableExists) {
        add(createSubTaskTagCrossRefTableSql())
        add(
            "CREATE INDEX IF NOT EXISTS `index_sub_task_tag_cross_ref_tagId` " +
                "ON `$SUB_TASK_TAG_CROSS_REF_TABLE` (`tagId`)",
        )
    }
}

/**
 * v13 的 `sub_tasks` 目标表结构（**必须与 `13.json` 的 Room 形状逐字一致**）。
 *
 * 只在「源表不存在」的兜底分支用到；DEFAULT 的写法尤其要逐字对齐
 * （写成 `DEFAULT '1'` 会与 Room 期望的 `defaultValue = "1"` 对不上，
 * 这种错编译期完全看不出来，只在迁移真跑时才炸 —— 见 [Migration_9_10] 的同类说明）。
 */
internal fun createSubTasksTableSql(tableName: String): String = """
    CREATE TABLE IF NOT EXISTS `$tableName` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `parentTaskId` INTEGER,
        `title` TEXT NOT NULL,
        `isCompleted` INTEGER NOT NULL,
        `order` INTEGER NOT NULL,
        `createdAt` INTEGER NOT NULL,
        `category` TEXT,
        `dueDate` INTEGER,
        `priority` INTEGER NOT NULL DEFAULT $DEFAULT_SUB_TASK_PRIORITY,
        `amount` REAL,
        `parentSubTaskId` INTEGER,
        FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
        FOREIGN KEY(`parentSubTaskId`) REFERENCES `$tableName`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
    )
""".trimIndent()

/**
 * v13 的 `sub_task_tag_cross_ref` 目标表结构（**必须与 `13.json` 的 Room 形状逐字一致**）。
 *
 * 联合主键 `(subTaskId, tagId)`；两侧都是 CASCADE —— 清单项删了 / 标签删了，关联行自动没。
 */
internal fun createSubTaskTagCrossRefTableSql(): String = """
    CREATE TABLE IF NOT EXISTS `$SUB_TASK_TAG_CROSS_REF_TABLE` (
        `subTaskId` INTEGER NOT NULL,
        `tagId` INTEGER NOT NULL,
        PRIMARY KEY(`subTaskId`, `tagId`),
        FOREIGN KEY(`subTaskId`) REFERENCES `$SUB_TASKS_TABLE`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
    )
""".trimIndent()

/**
 * 表是否存在（`sqlite_master` 只读查询，迁移事务内可用 —— 与 [Migration_8_9] 同款做法）。
 */
private fun subTasksTableExists(database: SupportSQLiteDatabase, tableName: String): Boolean =
    database.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'",
    ).use { cursor -> cursor.count > 0 }

/**
 * 表里是否已有该列（`PRAGMA table_info` 只读检查，迁移事务内可用）。
 *
 * 这是本轮幂等的关键：重复跑迁移不能撞 `duplicate column name: xxx`。
 */
private fun subTaskHasColumn(
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
