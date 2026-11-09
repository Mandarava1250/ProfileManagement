package com.example.personalassistant.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 21：Migration_15_16 语句层单测（JVM，不需要设备）
 *
 * 锁死「语句形状对」—— 真跑一遍迁移需要真 SQLite，
 * 那部分由 [com.example.personalassistant.core.database.Migration_15_16Test]（androidTest）负责。
 */
class Migration_15_16StatementsTest {

    @Test
    fun `adds linkedTaskId column to transactions`() {
        val statements = migration15To16Statements()

        assertTrue(
            "应给 transactions 加 linkedTaskId 列",
            statements.any {
                it.contains("ALTER TABLE `$TRANSACTIONS_TABLE`") &&
                    it.contains("ADD COLUMN `$LINKED_TASK_ID_COLUMN`")
            },
        )
    }

    @Test
    fun `linkedTaskId column is nullable without default`() {
        val alter = migration15To16Statements()
            .first { it.contains("ADD COLUMN") }

        assertTrue(
            "列类型应为 INTEGER",
            alter.contains("`$LINKED_TASK_ID_COLUMN` INTEGER"),
        )
        assertTrue(
            "不该带 DEFAULT —— Entity 侧是 Long?（无默认），带了 Room 会判 schema 不匹配",
            !alter.contains("DEFAULT"),
        )
        assertTrue(
            "不该带外键 —— 删任务时流水必须留下（弱引用）",
            !alter.contains("REFERENCES"),
        )
    }

    @Test
    fun `creates index on linkedTaskId`() {
        val statements = migration15To16Statements()

        assertTrue(
            "应建 linkedTaskId 索引",
            statements.any {
                it.contains("CREATE INDEX IF NOT EXISTS") &&
                    it.contains("index_${TRANSACTIONS_TABLE}_$LINKED_TASK_ID_COLUMN") &&
                    it.contains("ON `$TRANSACTIONS_TABLE` (`$LINKED_TASK_ID_COLUMN`)")
            },
        )
    }

    @Test
    fun `index name matches Room convention`() {
        // Room 按「index_表名_列名」生成索引名 —— 名字不一致会让迁移校验失败
        val index = migration15To16Statements()
            .first { it.contains("CREATE INDEX") }

        assertTrue(
            "索引名应为 index_transactions_linkedTaskId",
            index.contains("index_transactions_linkedTaskId"),
        )
    }

    @Test
    fun `migration is registered from 15 to 16`() {
        assertEquals(15, MIGRATION_15_16.startVersion)
        assertEquals(16, MIGRATION_15_16.endVersion)
    }
}
