package com.example.personalassistant.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration_13_14 SQL assertions (JVM only, no device needed).
 *
 * What this round is most sensitive about:
 *
 * 1. **Index names must match Room's auto-generated names character for character.**
 *    Room's default naming rule is `index_<table>_<col1>_<col2>` (columns joined by `_`,
 *    in declaration order). Room's migration validation compares the **index name AND the
 *    column set** of every index. A single wrong character → `Migration didn't properly
 *    handle: transactions` → crash on app start for every existing user.
 *    That failure is invisible at compile time, so it is pinned here.
 *
 * 2. **This migration must not touch table shape.** Only `CREATE INDEX` is allowed —
 *    no `ALTER TABLE`, no `CREATE TABLE`, no `DROP`. The whole point of this round is
 *    a pure performance change: not one byte of user data may change shape.
 *
 * 3. **Idempotency.** Every statement uses `CREATE INDEX IF NOT EXISTS`, so re-running
 *    the migration on an already-v14 database is a no-op.
 *
 * 4. **Column order inside a composite index matters.** SQLite can only use the index
 *    for filtering when the leading columns appear in the WHERE clause in the same order.
 */
class Migration_13_14StatementsTest {

    private val statements = migration13To14Statements()

    @Test
    fun `emits exactly four index statements`() {
        assertEquals("expected 4 CREATE INDEX statements", 4, statements.size)
        assertEquals(
            "every statement must be a CREATE INDEX",
            4,
            statements.count { it.startsWith("CREATE INDEX") },
        )
    }

    /**
     * 核心断言：迁移里的索引名必须等于 Room 的默认命名规则算出来的名字。
     *
     * Entity 侧改了列名 / 列序，这里就会红 —— 迁移语句必须跟着改。
     */
    @Test
    fun `index names follow the room default naming rule`() {
        val expected = listOf(
            expectedIndexName(TRANSACTIONS_TABLE, "transferPairId"),
            expectedIndexName(TRANSACTIONS_TABLE, "accountId", "categoryId"),
            expectedIndexName(TASKS_TABLE, "isCompleted", "priority", "dueDate"),
            expectedIndexName(SUB_TASKS_TABLE, "category"),
        )
        val actual = statements.map { indexNameOf(it) }
        assertEquals(
            "index names must match Room's `index_<table>_<cols>` rule exactly",
            expected,
            actual,
        )
    }

    /** 每条索引都要落在预期的表 + 预期的列上（列序也要对）。 */
    @Test
    fun `each index targets the intended table and columns`() {
        assertIndex(
            table = TRANSACTIONS_TABLE,
            columns = listOf("transferPairId"),
        )
        assertIndex(
            table = TRANSACTIONS_TABLE,
            columns = listOf("accountId", "categoryId"),
        )
        assertIndex(
            table = TASKS_TABLE,
            columns = listOf("isCompleted", "priority", "dueDate"),
            directions = listOf("ASC", "DESC", "ASC"),
        )
        assertIndex(
            table = SUB_TASKS_TABLE,
            columns = listOf("category"),
        )
    }

    /** 本轮是纯索引迁移：不得动表结构、不得删东西。 */
    @Test
    fun `migration does not alter table shape`() {
        assertTrue(
            "must not ALTER TABLE: $statements",
            statements.none { it.contains("ALTER TABLE") },
        )
        assertTrue(
            "must not CREATE TABLE: $statements",
            statements.none { it.contains("CREATE TABLE") },
        )
        assertTrue(
            "must not DROP anything: $statements",
            statements.none { it.contains("DROP") },
        )
        assertTrue(
            "must not DELETE anything: $statements",
            statements.none { it.contains("DELETE") },
        )
    }

    /** 幂等：重跑一次形状不变。 */
    @Test
    fun `every statement uses if not exists so re-running is safe`() {
        assertTrue(
            "every CREATE INDEX must be idempotent: $statements",
            statements.all { it.contains("CREATE INDEX IF NOT EXISTS") },
        )
    }

    /** 复合索引的列序不能写反 —— 写反了索引就用不上。 */
    @Test
    fun `composite index column order is preserved`() {
        assertIndex(
            table = TRANSACTIONS_TABLE,
            columns = listOf("accountId", "categoryId"),
        )
        assertIndex(
            table = TASKS_TABLE,
            columns = listOf("isCompleted", "priority", "dueDate"),
            directions = listOf("ASC", "DESC", "ASC"),
        )
    }

    // ===== 工具 =====

    /**
     * Room 的默认索引命名规则：`index_` + 表名 + `_` + 各列名用 `_` 连接。
     */
    private fun expectedIndexName(table: String, vararg columns: String): String =
        "index_${table}_${columns.joinToString("_")}"

    /** 从 `CREATE INDEX IF NOT EXISTS \`name\` ...` 里抠出索引名。 */
    private fun indexNameOf(statement: String): String =
        statement.substringAfter("CREATE INDEX IF NOT EXISTS `").substringBefore('`')

    /** 每条索引都要落在预期的表 + 预期的列上（列序也要对）。 */
    private fun assertIndex(table: String, columns: List<String>) {
        assertIndex(table, columns, List(columns.size) { "ASC" })
    }

    /**
     * 断言「恰好有一条」针对某表某列组的索引语句，并校验列序与排序方向。
     *
     * @param directions 每列的排序方向（"ASC" / "DESC"），缺省全 ASC。
     */
    private fun assertIndex(table: String, columns: List<String>, directions: List<String>) {
        // ⚠️ 不能按表名筛：`transactions` 上有两条索引，按表名会命中两条。
        // 所以先按「表 + 列组」算出 Room 的索引名，再按名字精确定位。
        val expectedName = expectedIndexName(table, *columns.toTypedArray())
        val matches = statements.filter { indexNameOf(it) == expectedName }
        check(matches.size == 1) {
            "期望恰好 1 条名为 $expectedName 的索引语句，实际 ${matches.size} 条；语句列表：$statements"
        }
        val statement = matches.single()
        val rawColumns = statement
            .substringAfter("ON `$table` (")
            .substringBefore(")")
            .split(",")
            .map { it.trim() }
        val actualColumns = rawColumns.map { it.substringBefore(' ').trim('`') }
        val actualDirections = rawColumns.map {
            if (it.contains("DESC")) "DESC" else "ASC"
        }
        assertEquals(
            "索引 $table 的列序必须一致（语句：$statement）",
            columns,
            actualColumns,
        )
        assertEquals(
            "索引 $table 的排序方向必须一致（语句：$statement）",
            directions,
            actualDirections,
        )
    }
}
