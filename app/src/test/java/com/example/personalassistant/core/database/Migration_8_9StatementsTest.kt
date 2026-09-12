package com.example.personalassistant.core.database

import com.example.personalassistant.feature.profile.data.local.AccountIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3（A 方案，永久回归网）：Migration_8_9 SQL 断言
 *
 * ## 为什么 JVM 层断 SQL 字符串
 * 本机无 AVD（adb devices / emulator -list-avds 均空），Room 的
 * `createMigrationTestDriver` 需要 instrumented 环境才能真跑。
 * 但本迁移最容易坏的地方恰恰是「语句本身写没写对」，而这些都能在 JVM 断言：
 * - 有没有给 4 张表都加列（漏一张 → 那张表查询直接崩）
 * - DEFAULT 是不是 1（不是 → 旧数据落错账户，等于丢数据）
 * - 两个旧 UNIQUE 索引有没有先 DROP（没有 → 第二账户写不进去）
 * - 复合 FK 与 parent 侧唯一索引在不在（缺 → Room 校验失败启动即崩）
 * - sqlite_sequence 修正有没有被移出语句列表（放回 SQL 常量 → 无 AUTOINCREMENT 数据的库回滚）
 *
 * ## 局限（必须诚实）
 * 这些断言证明「语句存在且形状正确」，**不证明**「在真实 SQLite 上按序执行成功」。
 * 后者仍待 androidTest（B 路线）补，见 DEV_LOG 台账标注。
 */
class Migration_8_9StatementsTest {

    private val statements = MIGRATION_8_9_STATEMENTS

    @Test
    fun `建 user_profiles 并插入默认账户 id 1`() {
        assertTrue(
            "应创建 user_profiles 表",
            statements.any { it.contains("CREATE TABLE IF NOT EXISTS `user_profiles`") },
        )

        val insert = statements.single { it.startsWith("INSERT OR IGNORE INTO `user_profiles`") }
        assertTrue("默认账户必须显式 id=1，否则旧数据找不到归属：$insert", insert.contains("VALUES (1,"))
        assertTrue(
            "默认账户名应与 AccountIds 常量一致",
            insert.contains("'${AccountIds.DEFAULT_ACCOUNT_NAME}'"),
        )
    }

    @Test
    fun `记账 4 张表都加 accountId 且 DEFAULT 1`() {
        listOf("transactions", "categories", "monthly_budgets", "daily_pools").forEach { table ->
            val alter = statements.single {
                it.startsWith("ALTER TABLE `$table` ADD COLUMN `accountId`")
            }
            assertTrue(
                "$table 的 accountId 必须 NOT NULL DEFAULT 1（否则旧行是 NULL，查询条件对不上）：$alter",
                alter.contains("NOT NULL DEFAULT 1"),
            )
        }
    }

    @Test
    fun `两个旧 UNIQUE 索引先 DROP 再建账户维度复合 UNIQUE`() {
        // 预算：yearMonth 唯一 → (accountId, yearMonth) 唯一
        assertTrue(
            "必须 DROP 旧的 monthly_budgets yearMonth 唯一索引",
            statements.any { it == "DROP INDEX IF EXISTS `index_monthly_budgets_yearMonth`" },
        )
        val budgetIdx = statements.single {
            it.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_budgets_accountId_yearMonth`")
        }
        assertTrue("预算唯一索引应含两列：$budgetIdx", budgetIdx.contains("(`accountId`, `yearMonth`)"))

        // 零钱池：date 唯一 → (accountId, date) 唯一
        assertTrue(
            "必须 DROP 旧的 daily_pools date 唯一索引",
            statements.any { it == "DROP INDEX IF EXISTS `index_daily_pools_date`" },
        )
        val poolIdx = statements.single {
            it.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_pools_accountId_date`")
        }
        assertTrue("零钱池唯一索引应含两列：$poolIdx", poolIdx.contains("(`accountId`, `date`)"))
    }

    @Test
    fun `categories 复合 FK 的 parent 唯一索引必须先于 transactions 重建建立`() {
        val parentIdx = statements.indexOfFirst {
            it.startsWith("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_accountId_id`")
        }
        val createNew = statements.indexOfFirst {
            it.contains("CREATE TABLE IF NOT EXISTS `transactions_new`")
        }
        assertTrue("categories(accountId,id) 唯一索引应存在", parentIdx >= 0)
        assertTrue(
            "parent 索引必须在 transactions_new 建表之前，否则 FK 解析不到索引",
            parentIdx in 0 until createNew,
        )
    }

    @Test
    fun `transactions 重建走建新表搬数据并带复合 FK`() {
        val createNew = statements.single { it.contains("CREATE TABLE IF NOT EXISTS `transactions_new`") }
        assertTrue("新表应有复合 FK 子列", createNew.contains("FOREIGN KEY(`accountId`, `categoryId`)"))
        assertTrue("新表复合 FK 应指向 categories(accountId, id)", createNew.contains("REFERENCES `categories`(`accountId`, `id`)"))
        assertTrue("应保留 RESTRICT（有流水的分类不许删）", createNew.contains("ON DELETE RESTRICT"))
        assertTrue("应保留 CASCADE 更新", createNew.contains("ON UPDATE CASCADE"))

        assertTrue(
            "应把旧数据整表搬进新表",
            statements.any { it.startsWith("INSERT INTO `transactions_new`") && it.contains("FROM `transactions`") },
        )
        assertTrue(statements.contains("DROP TABLE `transactions`"))
        assertTrue(statements.contains("ALTER TABLE `transactions_new` RENAME TO `transactions`"))
    }

    @Test
    fun `重建后 6 个 transactions 索引全部重建`() {
        // DROP TABLE 会连带删掉子表全部索引，漏建任何一个都会让 Room schema 校验失败
        listOf(
            "index_transactions_date",
            "index_transactions_type",
            "index_transactions_categoryId",
            "index_transactions_date_type",
            "index_transactions_type_categoryId_date",
            "index_transactions_accountId_date",
        ).forEach { name ->
            assertTrue(
                "缺少 $name 的重建语句（Room 会因索引缺失判定 schema 不匹配）",
                statements.any { it.contains("CREATE INDEX IF NOT EXISTS `$name`") },
            )
        }
    }

    @Test
    fun `sqlite_sequence 修正不得留在语句列表里`() {
        // 该表只在首次向 AUTOINCREMENT 表插入时才创建；
        // 若把 UPDATE 混进无条件的语句列表，「装过但从未记账」的库会抛 no such table 并回滚整个迁移。
        assertTrue(
            "UPDATE sqlite_sequence 必须由 fixTransactionAutoIncrementSequence 条件执行，不能进语句列表",
            statements.none { it.contains("UPDATE sqlite_sequence") },
        )
        assertTrue(SQL_FIX_SQLITE_SEQUENCE_NAME.contains("WHERE `name` = 'transactions_new'"))
        assertTrue(SQL_FIX_SQLITE_SEQUENCE_WATERMARK.contains("WHERE `name` = 'transactions'"))
    }

    @Test
    fun `迁移不应包含 PRAGMA foreign_keys 开关`() {
        // Room 把迁移包在事务里，事务内该 PRAGMA 是 no-op；写了只会误导后来者
        assertTrue(
            "不应出现 foreign_keys PRAGMA（事务内无效）",
            statements.none { it.contains("PRAGMA foreign_keys") },
        )
    }

    @Test
    fun `加列语句都排在依赖它们的索引与重建之前`() {
        val lastAlter = statements.indexOfLast { it.contains("ADD COLUMN `accountId`") }
        val firstCategoryIdx = statements.indexOfFirst { it.contains("ON `categories` (`accountId`") }
        val firstPoolIdx = statements.indexOfFirst { it.contains("ON `daily_pools` (`accountId`, `date`)") }
        assertTrue("categories 索引应在加列之后", firstCategoryIdx > lastAlter)
        assertTrue("daily_pools 索引应在加列之后", firstPoolIdx > lastAlter)
    }

    @Test
    fun `AccountIds 默认值与实体 accountId 默认值同源于常量`() {
        // 三处一致里可 JVM 校验的两处：常量本身 + 迁移 SQL 字面量
        assertEquals(1L, AccountIds.DEFAULT_ACCOUNT_ID)
        val insert = statements.single { it.startsWith("INSERT OR IGNORE INTO `user_profiles`") }
        assertTrue(insert.contains("VALUES (${AccountIds.DEFAULT_ACCOUNT_ID},"))
    }
}
