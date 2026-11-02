package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 14 → 15：Phase 16「任务 / 清单」两套模型合并
 *
 * ## 目标：三件事
 *
 * 1. 新建 `task_lists` 表（清单 = 任务归属地），并预置内置「收集箱」（id = 1）
 * 2. `tasks` 加 7 列：清单归属 / 父任务自引用 / 金额 / 时间段 / 完成时间 / 置顶
 * 3. **`sub_tasks` 整表并入 `tasks`**，`sub_task_tag_cross_ref` 并入 `task_tag_cross_ref`
 *
 * ## 第 3 件事：为什么必须做
 *
 * Phase 15 之前 App 里有两套并行模型：
 * - `tasks`：有提醒 / 重复 / 描述，但没金额、没清单归属
 * - `sub_tasks`：有分类 / 金额 / 嵌套，但没提醒、没重复
 *
 * 滴答清单（TickTick）的模型是 **清单 → 任务 → 子项** 三层。
 * 不合并这两套，「清单项」就永远是任务能力的残废版，收集箱 / 智能清单这些
 * 滴答核心体验一个都做不出来。
 *
 * ## 搬迁算法：恒定 id 偏移
 *
 * 设 `offset = MAX(tasks.id)`（空表时为 0），则 `sub_tasks` 的每一行
 * 搬到 `tasks` 后的新 id = `旧 id + offset`。
 *
 * **偏移对父子一致，所以一条规则通吃所有层级**：
 *
 * | sub_tasks 行类型 | 迁入后的 `parentTaskId` |
 * |---|---|
 * | `parentTaskId != NULL`（任务的子项） | 保持 `s.parentTaskId` 原值（指向**原有** tasks 行，id 不变） |
 * | `parentTaskId IS NULL AND parentSubTaskId IS NULL`（独立顶层清单项） | NULL（成为顶层任务） |
 * | `parentSubTaskId != NULL`（嵌套子项） | `s.parentSubTaskId + offset`（指向**搬迁来的**父行） |
 *
 * 新 id 区间是 `[offset+1, offset+MAX(sub_tasks.id)]`，与原有 id 区间 `[1, offset]` **不重叠** ✓
 *
 * ## 为什么先 INSERT 再 UPDATE 回填父指针
 *
 * `tasks.parentTaskId` 是**自引用外键**，而外键检查在迁移期间是**开着的**
 * （Android 默认 `PRAGMA foreign_keys = ON`）。
 *
 * 若一次性 `INSERT ... SELECT` 带父指针，嵌套子项可能先于其父项落库 → 外键违规 → 迁移抛异常。
 * SQLite 的 `PRAGMA defer_foreign_keys`（延迟到提交时检查）需要 SQLite 3.20+，
 * 而 minSdk 26 对应 SQLite 3.18，**用不了**；`PRAGMA foreign_keys` 在事务内又是 no-op。
 *
 * 所以拆成两步：
 * ① INSERT 全部行，`parentTaskId` 只填**不需要偏移**的那部分（任务子项保留原值）
 * ② UPDATE 回填嵌套行的 `parentTaskId` —— 此时所有行都已在表里，外键检查必然通过
 *
 * 这个写法还顺带支持了**任意深度**的嵌套（不假设最深 3 层）。
 *
 * ## SQLite 的 ADD COLUMN 限制（本迁移踩过的坑）
 *
 * SQLite 规定：**带 `REFERENCES` 的新列，其 DEFAULT 必须是 NULL**
 * （否则无法对既有行校验外键）。
 *
 * 这跟「`listId` 想写成 NOT NULL DEFAULT 1」直接冲突。
 * 本迁移的取舍：
 * - `listId`：`INTEGER DEFAULT 1 REFERENCES task_lists(id) ...` —— **可空 + 默认 1**
 *   （默认值非空时 SQLite 会跳过该列的外键检查，但 CASCADE 仍然生效，
 *   所以「删清单连带删任务」这个最重要的保证保住了；
 *   「不能挂到不存在的清单」由 Repository 层兜底）
 * - `parentTaskId`：`INTEGER REFERENCES tasks(id) ...` —— 可空、无默认 ✓
 *
 * Entity 侧必须与之一致：`listId` 声明成 `Long?` + `@ColumnInfo(defaultValue = "1")`。
 * 若声明成非空 `Long`，Room 期望 `NOT NULL` 而实际列可空 → 迁移校验直接炸。
 *
 * ## 幂等
 *
 * - 列：`PRAGMA table_info` 判源表有没有这一列，有就跳过 ADD
 * - 表 / 索引：全部用 `CREATE ... IF NOT EXISTS`
 * - 数据搬迁：`INSERT OR IGNORE` + `WHERE EXISTS` 兜底
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_14_15: Migration = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // offset 必须在迁移开始时算成**常量**再拼进 SQL。
        // 若写成子查询 `(SELECT MAX(id) FROM tasks)`，在 `INSERT INTO tasks SELECT ... FROM tasks`
        // 执行期间会被边插边算，新插入的行会把 MAX 顶高 → 偏移漂移 → id 错乱。
        val offset = database.query("SELECT COALESCE(MAX(`id`), 0) FROM `$TASKS_TABLE`").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

        val existingTaskColumns: Set<String> = TASKS_NEW_COLUMN_DDL.keys
            .filter { columnHasColumn(database, TASKS_TABLE, it) }
            .toSet()

        migration14To15Statements(
            offset = offset,
            existingTaskColumns = existingTaskColumns,
            taskListsTableExists = tableExists(database, TASK_LISTS_TABLE),
            subTasksTableExists = tableExists(database, SUB_TASKS_TABLE),
            subTaskTagTableExists = tableExists(database, SUB_TASK_TAG_CROSS_REF_TABLE),
        ).forEach { database.execSQL(it) }
    }
}

/**
 * 清单表名
 *
 * ⚠️ 任务表名 `TASKS_TABLE` 已在 [Migration_13_14] 里声明（同包 `internal const`），
 * 这里**不再重复声明** —— Kotlin 顶层常量重复声明会报 "Conflicting declarations"。
 */
internal const val TASK_LISTS_TABLE = "task_lists"

/**
 * v15 新增到 `tasks` 的 7 列 → ADD COLUMN 语句片段。
 *
 * 键 = 列名（与 `TaskEntity` 的 `@ColumnInfo(name = ...)` 逐字一致），
 * 值 = 该列的 DDL 片段（不含 `ALTER TABLE ... ADD COLUMN` 前缀）。
 *
 * ⚠️ `listId` 刻意**不写 NOT NULL** —— SQLite 规定带 REFERENCES 的新列
 * DEFAULT 必须是 NULL，写了 `NOT NULL DEFAULT 1` 迁移会直接报
 * "Cannot add a NOT NULL column with default value NULL" 之类的外键限制错误。
 * 「清单归属必须有值」由 Domain 层 + Repository 层保证。
 */
internal val TASKS_NEW_COLUMN_DDL: Map<String, String> = mapOf(
    // 清单归属（可空 + DEFAULT 1 = 收集箱；REFERENCES 见上方说明）
    "listId" to
        "`listId` INTEGER DEFAULT $INBOX_LIST_ID REFERENCES `$TASK_LISTS_TABLE`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE",
    // 父任务自引用（可空、无默认 —— SQLite 要求带 REFERENCES 的新列 DEFAULT 必须为 NULL）
    "parentTaskId" to
        "`parentTaskId` INTEGER REFERENCES `$TASKS_TABLE`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
    // 金额（REAL，可空）
    "amount" to "`amount` REAL",
    // 时间段起点（INTEGER 毫秒时间戳，可空）—— 有 startAt 才有「日历时段块」
    "startAt" to "`startAt` INTEGER",
    // 时间段终点（INTEGER 毫秒时间戳，可空）
    "endAt" to "`endAt` INTEGER",
    // 完成时间（INTEGER 毫秒时间戳，可空）—— 完成率统计要用
    "completedAt" to "`completedAt` INTEGER",
    // 置顶（INTEGER NOT NULL DEFAULT 0）
    "isPinned" to "`isPinned` INTEGER NOT NULL DEFAULT 0",
)

/** v15 新增列的顺序（迁移语句的发出顺序） */
internal val TASKS_NEW_COLUMNS: List<String> = TASKS_NEW_COLUMN_DDL.keys.toList()

/** 内置收集箱的固定 id（与 `TaskListEntity.INBOX_ID` 一致） */
internal const val INBOX_LIST_ID = 1L

/** 内置收集箱的规范化名 */
internal const val INBOX_LIST_NAME = "inbox"

/** 内置收集箱的显示名 */
internal const val INBOX_LIST_DISPLAY_NAME = "收集箱"

/** 内置收集箱的颜色（ARGB，樱花粉 —— 与默认标签色同款） */
internal const val INBOX_LIST_COLOR = 4294931508L // 0xFFFF69B4

/**
 * v15 的 `task_lists` 目标表结构（**必须与 Room 生成的形状逐字一致**）。
 *
 * Room 会把 `Long` 映射成 `INTEGER`，`Boolean` 映射成 `INTEGER NOT NULL`。
 */
internal fun createTaskListsTableSql(): String = """
    CREATE TABLE IF NOT EXISTS `$TASK_LISTS_TABLE` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `nameNormalized` TEXT NOT NULL,
        `displayName` TEXT NOT NULL,
        `colorHex` INTEGER NOT NULL,
        `iconKey` TEXT NOT NULL,
        `sortOrder` INTEGER NOT NULL,
        `isSystem` INTEGER NOT NULL,
        `isArchived` INTEGER NOT NULL,
        `createdAt` INTEGER NOT NULL
    )
""".trimIndent()

/**
 * 迁移语句序列（按序执行，抽成函数供 JVM 单测逐条断言 —— 沿用 [Migration_12_13] 的做法）。
 *
 * @param offset `tasks` 表当前 MAX(id)；搬迁后的新 id = 旧 id + offset
 * @param existingTaskColumns `tasks` **已有**的新列名集合（幂等用）
 * @param taskListsTableExists `task_lists` 是否已存在
 * @param subTasksTableExists `sub_tasks` 是否存在（不存在则跳过搬迁与 DROP）
 * @param subTaskTagTableExists `sub_task_tag_cross_ref` 是否存在
 *
 * 顺序有依赖，不要重排：
 * ① 建 `task_lists` + 索引
 * ② 插入内置收集箱
 * ③ `tasks` 逐列 ADD COLUMN
 * ④ 清理 `sub_tasks` 里的孤儿行（父指针指向不存在的行）
 * ⑤ `sub_tasks` 全量 INSERT 进 `tasks`（父指针先不填嵌套那部分）
 * ⑥ UPDATE 回填嵌套行的父指针
 * ⑦ `sub_task_tag_cross_ref` 并入 `task_tag_cross_ref`
 * ⑧ DROP 两张旧表
 * ⑨ 建 `tasks` 的新索引
 */
internal fun migration14To15Statements(
    offset: Long = 0L,
    existingTaskColumns: Set<String> = emptySet(),
    taskListsTableExists: Boolean = false,
    subTasksTableExists: Boolean = true,
    subTaskTagTableExists: Boolean = false,
): List<String> = buildList {
    // ===== ① 建 task_lists =====
    if (!taskListsTableExists) {
        add(createTaskListsTableSql())
    }
    add(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_task_lists_nameNormalized` " +
            "ON `$TASK_LISTS_TABLE` (`nameNormalized`)",
    )
    add(
        "CREATE INDEX IF NOT EXISTS `index_task_lists_sortOrder` " +
            "ON `$TASK_LISTS_TABLE` (`sortOrder`)",
    )

    // ===== ② 插入内置收集箱 =====
    // OR IGNORE：重复跑时 id=1 已存在就跳过
    // createdAt 用 SQLite 内置的当前时间，避免为「现在几点」传参（保持语句可断言）
    add(
        """
        INSERT OR IGNORE INTO `$TASK_LISTS_TABLE`
            (`id`, `nameNormalized`, `displayName`, `colorHex`, `iconKey`,
             `sortOrder`, `isSystem`, `isArchived`, `createdAt`)
        VALUES
            ($INBOX_LIST_ID, '$INBOX_LIST_NAME', '$INBOX_LIST_DISPLAY_NAME', $INBOX_LIST_COLOR, '',
             0, 1, 0, CAST(strftime('%s', 'now') AS INTEGER) * 1000)
        """.trimIndent(),
    )

    // ===== ③ tasks 加列 =====
    TASKS_NEW_COLUMN_DDL.forEach { (column, ddl) ->
        if (column !in existingTaskColumns) {
            add("ALTER TABLE `$TASKS_TABLE` ADD COLUMN $ddl")
        }
    }

    if (subTasksTableExists) {
        // ===== ④ 清理孤儿行 =====
        // parentSubTaskId 指向不存在的行（外键检查曾关闭时可能写进来）。
        // 不清掉的话，第 ⑤ 步的 JOIN 会漏行，数据静默丢失。
        add(
            "DELETE FROM `$SUB_TASKS_TABLE` WHERE `parentSubTaskId` IS NOT NULL " +
                "AND `parentSubTaskId` NOT IN (SELECT `id` FROM `$SUB_TASKS_TABLE`)",
        )

        // ===== ⑤ sub_tasks 全量搬进 tasks =====
        // 父指针：任务子项保留 s.parentTaskId 原值；嵌套行先留 NULL（第 ⑥ 步回填）
        add(moveSubTasksToTasksSql(offset))
        // ===== ⑥ 回填嵌套行的父指针 =====
        add(linkNestedSubTaskParentsSql(offset))

        // ===== ⑦ 标签关联并入 task_tag_cross_ref =====
        if (subTaskTagTableExists) {
            add(
                """
                INSERT OR IGNORE INTO `task_tag_cross_ref` (`taskId`, `tagId`)
                SELECT s.`subTaskId` + $offset, s.`tagId`
                FROM `$SUB_TASK_TAG_CROSS_REF_TABLE` s
                """.trimIndent(),
            )
        }

        // ===== ⑧ DROP 两张旧表 =====
        // 先删关联表再删主表：关联表的外键指向 sub_tasks
        if (subTaskTagTableExists) {
            add("DROP TABLE IF EXISTS `$SUB_TASK_TAG_CROSS_REF_TABLE`")
        }
        add("DROP TABLE IF EXISTS `$SUB_TASKS_TABLE`")
    }

    // ===== ⑨ tasks 新索引 =====
    add("CREATE INDEX IF NOT EXISTS `index_tasks_listId` ON `$TASKS_TABLE` (`listId`)")
    add("CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `$TASKS_TABLE` (`parentTaskId`)")
    add(
        "CREATE INDEX IF NOT EXISTS `index_tasks_listId_isCompleted_dueDate` " +
            "ON `$TASKS_TABLE` (`listId`, `isCompleted`, `dueDate`)",
    )
}

/**
 * `sub_tasks` → `tasks` 的搬迁 INSERT（父指针只填「不需要偏移」的那部分）。
 *
 * 字段映射：
 * - `id` ← `s.id + offset`
 * - `title` / `dueDate` / `priority` / `isCompleted` / `category` / `amount` 原样搬
 * - `description` / `reminderTime` / `repeatType` / `repeatEndDate` / `repeatDays` /
 *   `startAt` / `endAt` / `completedAt` —— 清单项本来就没有这些，填 NULL
 * - `manualOrder` ← `s.order`（清单项的显示顺序 → 手动排序权重）
 * - `holidayStrategy` ← `'SKIP'`（清单项没有重复规则，节假日策略无意义，用默认档）
 * - `listId` ← 1（收集箱 —— 用户之后自己分流）
 * - `parentTaskId` ← `s.parentTaskId`（任务子项保留原值；嵌套行为 NULL，第 ⑥ 步回填）
 */
internal fun moveSubTasksToTasksSql(offset: Long): String = """
    INSERT INTO `$TASKS_TABLE` (
        `id`, `title`, `description`, `dueDate`, `priority`, `isCompleted`, `category`,
        `reminderTime`, `createdAt`, `updatedAt`, `repeatType`, `repeatEndDate`, `repeatDays`,
        `manualOrder`, `holidayStrategy`, `listId`, `parentTaskId`, `amount`,
        `startAt`, `endAt`, `completedAt`, `isPinned`
    )
    SELECT
        s.`id` + $offset, s.`title`, NULL, s.`dueDate`, s.`priority`, s.`isCompleted`, s.`category`,
        NULL, s.`createdAt`, s.`createdAt`, NULL, NULL, NULL,
        s.`order`, 'SKIP', $INBOX_LIST_ID, s.`parentTaskId`, s.`amount`,
        NULL, NULL, NULL, 0
    FROM `$SUB_TASKS_TABLE` s
""".trimIndent()

/**
 * 回填嵌套子项的父指针。
 *
 * 第 ⑤ 步之后所有搬迁行都已在 `tasks` 表里，此时再 SET `parentTaskId`，
 * 外键检查必然通过（父行就在表里）—— 这是绕开「外键检查开着 + 不能延迟检查」的关键。
 *
 * `s.id + offset = tasks.id` 是 1:1 对应关系，因此这个 UPDATE **只命中搬迁来的行**：
 * 原有 tasks 行的 id ≤ offset，而 `s.id + offset ≥ offset + 1` ✓
 */
internal fun linkNestedSubTaskParentsSql(offset: Long): String = """
    UPDATE `$TASKS_TABLE` SET `parentTaskId` = (
        SELECT s.`parentSubTaskId` + $offset
        FROM `$SUB_TASKS_TABLE` s
        WHERE s.`id` + $offset = `$TASKS_TABLE`.`id`
    )
    WHERE EXISTS (
        SELECT 1 FROM `$SUB_TASKS_TABLE` s
        WHERE s.`id` + $offset = `$TASKS_TABLE`.`id` AND s.`parentSubTaskId` IS NOT NULL
    )
""".trimIndent()

/**
 * 表是否存在（`sqlite_master` 只读查询，迁移事务内可用 —— 与 [Migration_8_9] 同款做法）。
 */
private fun tableExists(database: SupportSQLiteDatabase, tableName: String): Boolean =
    database.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'",
    ).use { cursor -> cursor.count > 0 }

/**
 * 表里是否已有该列（`PRAGMA table_info` 只读检查，迁移事务内可用）。
 *
 * 这是幂等的关键：重复跑迁移不能撞 `duplicate column name: xxx`。
 */
private fun columnHasColumn(
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
