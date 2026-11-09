package com.example.personalassistant.core.database

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 D：Migration 18 → 19 的 DDL 断言
 *
 * 为什么要测这个：Room 在**运行期**才校验「迁移出来的表」与「Entity 声明的表」是否一致，
 * 编译期看不出来 —— DDL 写错一个字符就是线上用户一升级就崩。
 * 所以把关键片段断言在 JVM 单测里。
 */
class Migration_18_19Test {

    @Test
    fun `creates coin_pool_settlements table`() {
        val sql = createCoinPoolSettlementsTableSql()
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `coin_pool_settlements`"))
        assertTrue(sql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
        // ⚠️ 必须带 DEFAULT 1 —— 实体上声明了 @ColumnInfo(defaultValue = "1")，
        // Room 生成的 19.json 里就是 `accountId` INTEGER NOT NULL DEFAULT 1。
        // 漏掉 DEFAULT → Room 运行期校验抛 Migration didn't properly handle: coin_pool_settlements。
        assertTrue(sql.contains("`accountId` INTEGER NOT NULL DEFAULT 1"))
        assertTrue(sql.contains("`date` INTEGER NOT NULL"))
        // Double → REAL NOT NULL
        assertTrue(sql.contains("`amount` REAL NOT NULL"))
        // String → TEXT NOT NULL（ym 是账期标签，非空）
        assertTrue(sql.contains("`ym` TEXT NOT NULL"))
        assertTrue(sql.contains("`createdAt` INTEGER NOT NULL"))
        // 外键
        assertTrue(sql.contains("FOREIGN KEY(`accountId`) REFERENCES `user_profiles`(`id`)"))
        assertTrue(sql.contains("ON UPDATE NO ACTION ON DELETE CASCADE"))
    }

    @Test
    fun `creates unique index on accountId and date`() {
        val statements = migration18To19Statements()
        val indexSql = statements.first { it.contains("CREATE UNIQUE INDEX") }
        assertTrue(indexSql.contains("index_coin_pool_settlements_accountId_date"))
        assertTrue(indexSql.contains("`coin_pool_settlements` (`accountId`, `date`)"))
    }

    @Test
    fun `alters coin_pools adding lastSettledDate with default`() {
        val sql = alterCoinPoolsAddLastSettledDateSql()
        assertTrue(sql.contains("ALTER TABLE `coin_pools` ADD COLUMN `lastSettledDate`"))
        // ⚠️ DEFAULT 0 不能省：
        // 1) SQLite 不允许给已有表加「NOT NULL 且无默认值」的列
        // 2) 实体上声明的是 @ColumnInfo(defaultValue = "0")，两边必须一致
        assertTrue(sql.contains("`lastSettledDate` INTEGER NOT NULL DEFAULT 0"))
    }

    @Test
    fun `create statements are idempotent`() {
        migration18To19Statements().filter { it.contains("CREATE") }.forEach {
            assertTrue("语句必须带 IF NOT EXISTS：$it", it.contains("IF NOT EXISTS"))
        }
    }

    @Test
    fun `does not drop any table`() {
        migration18To19Statements().forEach {
            assertTrue(
                "本次迁移不得删表：$it",
                !it.contains("DROP TABLE"),
            )
        }
    }

    @Test
    fun `alters only coin_pools`() {
        // 只允许 ALTER coin_pools（加水位线列），不得动别的已有表
        val alters = migration18To19Statements().filter { it.contains("ALTER TABLE") }
        assertTrue(alters.size == 1)
        assertTrue(alters.first().contains("ALTER TABLE `coin_pools`"))
    }
}
