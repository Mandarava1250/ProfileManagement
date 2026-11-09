package com.example.personalassistant.core.database

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 C：Migration 17 → 18 的 DDL 断言
 *
 * 为什么要测这个：Room 在**运行期**才校验「迁移出来的表」与「Entity 声明的表」是否一致，
 * 编译期看不出来 —— DDL 写错一个字符就是线上用户一升级就崩。
 * 所以把关键片段断言在 JVM 单测里。
 */
class Migration_17_18Test {

    @Test
    fun `creates coin_pools table`() {
        val sql = createCoinPoolsTableSql()
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `coin_pools`"))
        assertTrue(sql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
        // ⚠️ 必须带 DEFAULT 1 —— 实体上声明了 @ColumnInfo(defaultValue = "1")，
        // Room 生成的 18.json 里就是 `accountId` INTEGER NOT NULL DEFAULT 1。
        // 漏掉 DEFAULT → Room 运行期校验抛 Migration didn't properly handle: coin_pools。
        assertTrue(sql.contains("`accountId` INTEGER NOT NULL DEFAULT 1"))
        // Double → REAL NOT NULL
        assertTrue(sql.contains("`balance` REAL NOT NULL"))
        // String? → TEXT（可空，无 NOT NULL）
        assertTrue(sql.contains("`note` TEXT"))
        assertTrue(sql.contains("`createdAt` INTEGER NOT NULL"))
        assertTrue(sql.contains("`updatedAt` INTEGER NOT NULL"))
        // 外键
        assertTrue(sql.contains("FOREIGN KEY(`accountId`) REFERENCES `user_profiles`(`id`)"))
        assertTrue(sql.contains("ON UPDATE NO ACTION ON DELETE CASCADE"))
    }

    @Test
    fun `creates unique index on accountId`() {
        val statements = migration17To18Statements()
        val indexSql = statements.first { it.contains("CREATE UNIQUE INDEX") }
        assertTrue(indexSql.contains("index_coin_pools_accountId"))
        assertTrue(indexSql.contains("`coin_pools` (`accountId`)"))
    }

    @Test
    fun `all statements are idempotent`() {
        migration17To18Statements().forEach {
            assertTrue("语句必须带 IF NOT EXISTS：$it", it.contains("IF NOT EXISTS"))
        }
    }

    @Test
    fun `does not touch existing tables`() {
        migration17To18Statements().forEach {
            assertTrue(
                "本次迁移只允许新建表，不得改动已有表：$it",
                !it.contains("ALTER TABLE") && !it.contains("DROP TABLE"),
            )
        }
    }
}
