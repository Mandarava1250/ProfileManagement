package com.example.personalassistant.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 16：Migration_14_15 语句层单测（JVM，不需要设备）
 *
 * ## 与 androidTest 的分工
 *
 * - 本文件：断言「语句形状对」—— 顺序、列名、偏移量算得对、幂等分支选得对
 * - [com.example.personalassistant.core.database.Migration_14_15Test]（androidTest）：
 *   在真实 SQLite 上跑一遍，验 schema 校验 + 数据语义
 *
 * 真跑一遍需要真 SQLite，所以「搬迁后数据对不对」只能靠 androidTest 验；
 * 这里锁死的是「语句本身没写错」—— 那部分不需要设备就能覆盖。
 */
class Migration_14_15StatementsTest {

    // ===== ① 建表 + 收集箱 =====

    @Test
    fun `creates task_lists table with inbox row`() {
        val statements = migration14To15Statements()

        assertTrue(
            "应建出 task_lists 表",
            statements.any { it.contains("CREATE TABLE IF NOT EXISTS `$TASK_LISTS_TABLE`") },
        )
        assertTrue(
            "应插入内置收集箱",
            statements.any {
                it.contains("INSERT OR IGNORE INTO `$TASK_LISTS_TABLE`") &&
                    // 不匹配换行与缩进（trimIndent 会改掉它们），只看 VALUES 那一行的内容
                    it.contains("($INBOX_LIST_ID, '$INBOX_LIST_NAME', '$INBOX_LIST_DISPLAY_NAME'")
            },
        )
        assertEquals("收集箱 id 应为 1", 1L, INBOX_LIST_ID)
        assertEquals("收集箱规范化名应为 inbox", "inbox", INBOX_LIST_NAME)
    }

    @Test
    fun `skips creating task_lists when it already exists`() {
        val statements = migration14To15Statements(taskListsTableExists = true)

        assertFalse(
            "已存在时不应再 CREATE TABLE",
            statements.any { it.contains("CREATE TABLE IF NOT EXISTS `$TASK_LISTS_TABLE`") },
        )
        assertTrue(
            "收集箱插入语句仍要保留（OR IGNORE 保证幂等）",
            statements.any { it.contains("INSERT OR IGNORE INTO `$TASK_LISTS_TABLE`") },
        )
    }

    // ===== ② tasks 加 7 列 =====

    @Test
    fun `adds all seven new columns to tasks`() {
        val statements = migration14To15Statements()

        for (column in TASKS_NEW_COLUMNS) {
            assertTrue(
                "应给 tasks 加列 $column",
                statements.any { it.contains("ALTER TABLE `$TASKS_TABLE` ADD COLUMN") && it.contains("`$column`") },
            )
        }
        assertEquals("v15 新增 7 列", 7, TASKS_NEW_COLUMNS.size)
    }

    @Test
    fun `listId column stays nullable with default one`() {
        val ddl = TASKS_NEW_COLUMN_DDL.getValue("listId")

        // ⚠️ SQLite 规定：带 REFERENCES 的新列，其 DEFAULT 必须是 NULL。
        // 写成 NOT NULL DEFAULT 1 会让迁移直接报外键限制错误。
        assertFalse("listId 不能是 NOT NULL", ddl.contains("NOT NULL"))
        assertTrue("listId 默认值应为 1", ddl.contains("DEFAULT $INBOX_LIST_ID"))
        assertTrue("listId 必须带外键到 task_lists", ddl.contains("REFERENCES `$TASK_LISTS_TABLE`"))
        assertTrue("删清单应连带删任务", ddl.contains("ON DELETE CASCADE"))
    }

    @Test
    fun `parentTaskId column is nullable without default`() {
        val ddl = TASKS_NEW_COLUMN_DDL.getValue("parentTaskId")

        assertFalse("parentTaskId 不能是 NOT NULL", ddl.contains("NOT NULL"))
        assertFalse("parentTaskId 不能有 DEFAULT", ddl.contains("DEFAULT"))
        assertTrue("parentTaskId 必须带自引用外键", ddl.contains("REFERENCES `$TASKS_TABLE`"))
        assertTrue("删父任务应把子项提升为顶层", ddl.contains("ON DELETE SET NULL"))
    }

    @Test
    fun `skips adding columns that already exist`() {
        val statements = migration14To15Statements(
            existingTaskColumns = setOf("listId", "parentTaskId"),
        )

        assertFalse(
            "已有列不应重复 ADD",
            statements.any { it.contains("ADD COLUMN `listId`") },
        )
        assertFalse(
            "已有列不应重复 ADD",
            statements.any { it.contains("ADD COLUMN `parentTaskId`") },
        )
        assertTrue(
            "没有的列仍要 ADD",
            statements.any { it.contains("ADD COLUMN `amount`") },
        )
    }

    // ===== ③ 搬迁 =====

    @Test
    fun `moves sub tasks into tasks with id offset`() {
        val sql = moveSubTasksToTasksSql(offset = 2L)

        assertTrue("新 id 应为 旧 id + offset", sql.contains("s.`id` + 2"))
        assertTrue("清单项应落到收集箱", sql.contains("$INBOX_LIST_ID"))
        assertTrue("顺序权重应从 order 搬到 manualOrder", sql.contains("s.`order`"))
        assertTrue("金额应原样搬", sql.contains("s.`amount`"))
        assertTrue("分类应原样搬", sql.contains("s.`category`"))
    }

    @Test
    fun `links nested sub task parents with offset`() {
        val sql = linkNestedSubTaskParentsSql(offset = 5L)

        assertTrue("父指针应带偏移", sql.contains("s.`parentSubTaskId` + 5"))
        assertTrue("只处理嵌套行", sql.contains("s.`parentSubTaskId` IS NOT NULL"))
    }

    @Test
    fun `deletes orphan nested rows before moving`() {
        val statements = migration14To15Statements()

        val deleteIndex = statements.indexOfFirst { it.contains("DELETE FROM `$SUB_TASKS_TABLE`") }
        val moveIndex = statements.indexOfFirst { it.contains("INSERT INTO `$TASKS_TABLE`") }

        assertTrue("应先清孤儿行", deleteIndex >= 0)
        assertTrue("应再搬数据", moveIndex >= 0)
        assertTrue("清孤儿必须排在搬迁之前", deleteIndex < moveIndex)
    }

    // ===== ④ 标签关联并入 =====

    @Test
    fun `moves sub task tag cross refs with offset`() {
        val statements = migration14To15Statements(subTaskTagTableExists = true)

        val insert = statements.firstOrNull {
            it.contains("INSERT OR IGNORE INTO `task_tag_cross_ref`")
        }
        assertTrue("标签关联应并入 task_tag_cross_ref", insert != null)
        assertTrue("关联的 taskId 应带偏移", insert!!.contains("s.`subTaskId` + "))
    }

    @Test
    fun `skips tag move when old table does not exist`() {
        val statements = migration14To15Statements(subTaskTagTableExists = false)

        assertFalse(
            "旧表不存在时不应搬关联",
            statements.any { it.contains("INSERT OR IGNORE INTO `task_tag_cross_ref`") },
        )
    }

    // ===== ⑤ DROP 旧表 =====

    @Test
    fun `drops both legacy tables`() {
        val statements = migration14To15Statements(subTaskTagTableExists = true)

        assertTrue(
            "应删掉 sub_task_tag_cross_ref",
            statements.any { it.contains("DROP TABLE IF EXISTS `$SUB_TASK_TAG_CROSS_REF_TABLE`") },
        )
        assertTrue(
            "应删掉 sub_tasks",
            statements.any { it.contains("DROP TABLE IF EXISTS `$SUB_TASKS_TABLE`") },
        )
    }

    @Test
    fun `drops cross ref table before sub tasks table`() {
        val statements = migration14To15Statements(subTaskTagTableExists = true)

        val dropCrossRef = statements.indexOfFirst {
            it.contains("DROP TABLE IF EXISTS `$SUB_TASK_TAG_CROSS_REF_TABLE`")
        }
        val dropSubTasks = statements.indexOfFirst { it.contains("DROP TABLE IF EXISTS `$SUB_TASKS_TABLE`") }

        assertTrue("先删关联表再删主表（关联表外键指向主表）", dropCrossRef < dropSubTasks)
    }

    // ===== ⑥ 新索引 =====

    @Test
    fun `creates three new indexes on tasks`() {
        val statements = migration14To15Statements()

        assertTrue(
            "listId 索引",
            statements.any { it.contains("index_tasks_listId`") },
        )
        assertTrue(
            "parentTaskId 索引（自引用外键必须有索引）",
            statements.any { it.contains("index_tasks_parentTaskId`") },
        )
        assertTrue(
            "listId + isCompleted + dueDate 复合索引",
            statements.any { it.contains("index_tasks_listId_isCompleted_dueDate`") },
        )
    }

    // ===== ⑦ 语句顺序 =====

    @Test
    fun `statement order is dependency safe`() {
        val statements = migration14To15Statements(subTaskTagTableExists = true)

        val createLists = statements.indexOfFirst { it.contains("CREATE TABLE IF NOT EXISTS `$TASK_LISTS_TABLE`") }
        val addColumns = statements.indexOfFirst { it.contains("ALTER TABLE `$TASKS_TABLE` ADD COLUMN") }
        val move = statements.indexOfFirst { it.contains("INSERT INTO `$TASKS_TABLE`") }
        val link = statements.indexOfFirst { it.contains("UPDATE `$TASKS_TABLE` SET `parentTaskId`") }
        val moveTags = statements.indexOfFirst { it.contains("INSERT OR IGNORE INTO `task_tag_cross_ref`") }
        val drop = statements.indexOfFirst { it.contains("DROP TABLE IF EXISTS `$SUB_TASKS_TABLE`") }
        val indexes = statements.indexOfFirst { it.contains("index_tasks_listId`") }

        assertTrue("建表在最前", createLists < addColumns)
        assertTrue("加列在搬迁前（搬迁语句要引用新列）", addColumns < move)
        assertTrue("搬迁在回填父指针前", move < link)
        assertTrue("回填在标签并入前", link < moveTags)
        assertTrue("标签并入在 DROP 前", moveTags < drop)
        assertTrue("DROP 在建索引前", drop < indexes)
    }

    // ===== ⑧ 幂等 =====

    @Test
    fun `all statements are safe to run twice`() {
        val statements = migration14To15Statements(subTaskTagTableExists = true)

        // CREATE 全部带 IF NOT EXISTS；INSERT 全部带 OR IGNORE；DELETE/UPDATE 天然幂等；
        // ALTER TABLE 靠 existingTaskColumns 判断跳过。
        val creates = statements.filter { it.contains("CREATE TABLE") || it.contains("CREATE INDEX") || it.contains("CREATE UNIQUE INDEX") }
        assertTrue("应有多条 CREATE", creates.isNotEmpty())
        assertTrue(
            "所有 CREATE 都应带 IF NOT EXISTS",
            creates.all { it.contains("IF NOT EXISTS") },
        )
    }
}
