package com.example.personalassistant.core.database

import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration_12_13 SQL assertions (JVM only, no device needed).
 *
 * What this round is most sensitive about:
 * 1. **Column order / completeness**: 5 new columns must all be added, in the declared order.
 * 2. **Idempotency**: re-running the migration must not emit `duplicate column name` statements
 *    (columns are skipped when already present, tables/indexes use `IF NOT EXISTS`).
 * 3. **`priority` default**: NOT NULL column added to a table that already has rows
 *    -> SQLite requires a non-NULL DEFAULT; the value must be `1` (MEDIUM),
 *    and it must be written **without quotes** so Room's `defaultValue = "1"` matches.
 * 4. **Self-referencing FK**: `parentSubTaskId` -> `sub_tasks(id)` ON DELETE SET NULL,
 *    and it must NOT carry a DEFAULT (SQLite forbids a non-NULL default on a REFERENCES column).
 * 5. **Cross-ref table shape**: composite PK `(subTaskId, tagId)` + two CASCADE FKs + tagId index.
 */
class Migration_12_13StatementsTest {

    /** Fresh v12 database: none of the new columns exist yet. */
    private val fresh = migration12To13Statements()

    /** A database that already has every new column (migration re-run). */
    private val alreadyMigrated = migration12To13Statements(
        existingColumns = setOf("category", "dueDate", "priority", "amount", "parentSubTaskId"),
        subTaskTagTableExists = true,
    )

    @Test
    fun `adds five new columns in declared order`() {
        val alters = fresh.filter { it.startsWith("ALTER TABLE") }
        assertEquals("expected 5 ADD COLUMN statements", 5, alters.size)
        assertTrue(alteredColumnNames(alters).containsAll(listOf("category", "dueDate", "priority", "amount", "parentSubTaskId")))
        assertEquals(
            "column order must stay stable",
            listOf("category", "dueDate", "priority", "amount", "parentSubTaskId"),
            alteredColumnNames(alters),
        )
    }

    @Test
    fun `priority column is not null with default one`() {
        val priority = alterStatementFor(fresh, "priority")
        assertTrue("priority must be NOT NULL: $priority", priority.contains("`priority` INTEGER NOT NULL"))
        assertTrue("priority must have DEFAULT 1: $priority", priority.contains("DEFAULT 1"))
        assertFalse(
            "default must not be quoted (Room expects defaultValue = \"1\"): $priority",
            priority.contains("DEFAULT '1'"),
        )
        assertEquals("default priority must match the shared constant", DEFAULT_SUB_TASK_PRIORITY, 1)
    }

    @Test
    fun `nullable columns have no default`() {
        listOf("category", "dueDate", "amount").forEach { column ->
            val statement = alterStatementFor(fresh, column)
            assertFalse("$column must not be NOT NULL: $statement", statement.contains("NOT NULL"))
            assertFalse("$column must not carry a DEFAULT: $statement", statement.contains("DEFAULT"))
        }
    }

    @Test
    fun `parent column references sub_tasks with set null and no default`() {
        val parent = alterStatementFor(fresh, "parentSubTaskId")
        assertTrue(
            "must reference sub_tasks: $parent",
            parent.contains("REFERENCES `$SUB_TASKS_TABLE`(`id`)"),
        )
        assertTrue("must be ON DELETE SET NULL: $parent", parent.contains("ON DELETE SET NULL"))
        assertFalse("REFERENCES column must not carry a DEFAULT: $parent", parent.contains("DEFAULT"))
        // ON DELETE SET NULL 要求该列可空 —— 写成 NOT NULL 的话，删父项时 SQLite 会直接报约束冲突
        assertFalse("SET NULL 列不能是 NOT NULL: $parent", parent.contains("NOT NULL"))
    }

    /**
     * 回归用例：`parentSubTaskId` 这个列名**同时**出现在 ADD COLUMN 语句和它自己的索引语句里。
     *
     * 旧写法 `fresh.single { it.contains("`parentSubTaskId`") }` 因此会命中两条，
     * `single` 直接抛 `IllegalArgumentException: Collection contains more than one matching element`
     * —— 这是本文件唯一炸掉的用例的根因。这里把它钉死，免得有人改回按列名 grep 整份语句列表。
     */
    @Test
    fun `parent column name also appears in its index statement`() {
        val mentions = fresh.filter { it.contains("`parentSubTaskId`") }
        assertEquals("父列名会出现在 2 条语句里（ADD COLUMN + 索引）", 2, mentions.size)
        assertEquals("其中 1 条是 ADD COLUMN", 1, mentions.count { it.startsWith("ALTER TABLE") })
        assertEquals("其中 1 条是索引", 1, mentions.count { it.contains("CREATE INDEX") })
    }

    @Test
    fun `creates cross ref table with composite primary key and cascade foreign keys`() {
        val create = createTableFor(fresh, SUB_TASK_TAG_CROSS_REF_TABLE)
        assertTrue("composite primary key required: $create", create.contains("PRIMARY KEY(`subTaskId`, `tagId`)"))
        assertTrue("subTaskId FK required: $create", create.contains("FOREIGN KEY(`subTaskId`) REFERENCES `sub_tasks`(`id`)"))
        assertTrue("tagId FK required: $create", create.contains("FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`)"))
        assertTrue("both FKs must CASCADE: $create", create.split("ON DELETE").size == 3)
        assertTrue("subTaskId must be NOT NULL: $create", create.contains("`subTaskId` INTEGER NOT NULL"))
        assertTrue("tagId must be NOT NULL: $create", create.contains("`tagId` INTEGER NOT NULL"))
    }

    @Test
    fun `creates indexes for parent column and cross ref table`() {
        assertTrue(
            "parentSubTaskId index required",
            fresh.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sub_tasks_parentSubTaskId`") },
        )
        assertTrue(
            "cross ref tagId index required",
            fresh.any { it.contains("CREATE INDEX IF NOT EXISTS `index_sub_task_tag_cross_ref_tagId`") },
        )
    }

    @Test
    fun `statement order is alter columns then index then cross ref table then its index`() {
        val kinds = fresh.map { statement ->
            when {
                statement.startsWith("ALTER TABLE") -> "ALTER"
                statement.contains("CREATE TABLE") -> "CREATE_TABLE"
                statement.contains("CREATE INDEX") -> "CREATE_INDEX"
                else -> "OTHER"
            }
        }
        assertEquals(
            "statement order must stay stable",
            listOf("ALTER", "ALTER", "ALTER", "ALTER", "ALTER", "CREATE_INDEX", "CREATE_TABLE", "CREATE_INDEX"),
            kinds,
        )
    }

    @Test
    fun `re-running the migration emits no duplicate column statements`() {
        assertTrue(
            "already-migrated database must not ADD columns again: $alreadyMigrated",
            alreadyMigrated.none { it.contains("ADD COLUMN") },
        )
        assertTrue(
            "already-migrated database must not re-create the cross ref table: $alreadyMigrated",
            alreadyMigrated.none { it.contains("CREATE TABLE") },
        )
    }

    @Test
    fun `all create statements use if not exists so re-running is safe`() {
        val creates = fresh.filter { it.startsWith("CREATE") }
        assertTrue("expected CREATE statements", creates.isNotEmpty())
        assertTrue(
            "every CREATE must be idempotent: $creates",
            creates.all { it.contains("IF NOT EXISTS") },
        )
    }

    @Test
    fun `missing source table falls back to creating the v13 shape`() {
        val statements = migration12To13Statements(existingColumns = null)
        val create = createTableFor(statements, SUB_TASKS_TABLE)
        assertTrue("all 5 new columns must exist: $create", listOf("category", "dueDate", "priority", "amount", "parentSubTaskId").all { create.contains("`$it`") })
        assertTrue("priority default required: $create", create.contains("`priority` INTEGER NOT NULL DEFAULT 1"))
        assertTrue("self FK required: $create", create.contains("FOREIGN KEY(`parentSubTaskId`) REFERENCES `$SUB_TASKS_TABLE`(`id`)"))
        assertTrue("no ALTER may be emitted when the table is missing", statements.none { it.contains("ADD COLUMN") })
    }

    /**
     * 老数据默认值：v12 里已存在的行迁移后 `priority` 必须落成 1（中优先级）。
     *
     * 这一条只能从「迁移 SQL 的 DEFAULT」+「实体的默认值」+「域模型的语义」三处对齐来保证，
     * 真跑一遍需要真 SQLite（androidTest），JVM 这里锁死三个常量必须一致。
     */
    @Test
    fun `legacy rows fall back to medium priority`() {
        // 1. 迁移 SQL 里写死的默认值
        assertEquals(1, DEFAULT_SUB_TASK_PRIORITY)
        // 2. 实体默认值（Kotlin 默认参数）必须跟迁移一致，否则 Room 校验会报
        //    "Migration didn't properly handle: sub_tasks"
        val entity = SubTaskEntity(parentTaskId = null, title = "旧行")
        assertEquals(DEFAULT_SUB_TASK_PRIORITY, entity.priority)
        // 3. 域模型语义：1 = 中优先级（TaskPriority.fromDb(1) 必须是 MEDIUM）
        assertEquals(TaskPriority.MEDIUM.dbValue, DEFAULT_SUB_TASK_PRIORITY)
        assertEquals(TaskPriority.MEDIUM, TaskPriority.fromDb(DEFAULT_SUB_TASK_PRIORITY))
    }

    // ===== 取语句的小工具 =====

    /**
     * 取「给某列 ADD COLUMN」的那条 ALTER 语句。
     *
     * ⚠️ 不能写 `statements.single { it.contains("`$column`") }`：
     * 列名会**同时**出现在 ADD COLUMN 语句和它自己的索引语句里
     * （`index_sub_tasks_parentSubTaskId` 里就有 `parentSubTaskId`），
     * `single` 命中两条会抛 `IllegalArgumentException: Collection contains more than one matching element`。
     *
     * 所以这里按「语句类型 + ADD COLUMN 后的第一个 token」精确定位，
     * 命中数不等于 1 时给出可读的报错，而不是让人猜。
     */
    private fun alterStatementFor(statements: List<String>, column: String): String {
        val matches = statements.filter {
            it.startsWith("ALTER TABLE") && it.contains("ADD COLUMN") && addedColumnOf(it) == column
        }
        check(matches.size == 1) {
            "期望恰好 1 条给 $column 的 ADD COLUMN 语句，实际 ${matches.size} 条；语句列表：$statements"
        }
        return matches.single()
    }

    /** 取某张表的建表语句（同样避开 `single` 的多命中坑）。 */
    private fun createTableFor(statements: List<String>, tableName: String): String {
        val matches = statements.filter { it.startsWith("CREATE TABLE IF NOT EXISTS `$tableName`") }
        check(matches.size == 1) {
            "期望恰好 1 条 $tableName 的建表语句，实际 ${matches.size} 条；语句列表：$statements"
        }
        return matches.single()
    }

    /** ALTER 语句里 ADD COLUMN 的列名。 */
    private fun addedColumnOf(statement: String): String {
        val afterAdd = statement.substringAfter("ADD COLUMN").trim()
        return afterAdd.substringBefore(' ').trim('`')
    }

    /** 从 ALTER 语句里抠出列名。 */
    private fun alteredColumnNames(statements: List<String>): List<String> =
        statements.filter { it.startsWith("ALTER TABLE") }.map { addedColumnOf(it) }
}
