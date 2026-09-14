package com.example.personalassistant.core.database

import com.example.personalassistant.feature.finance.data.seed.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5：Migration_9_10 SQL 断言（沿用 [Migration_8_9StatementsTest] 的 A 方案思路）
 *
 * ## 为什么 JVM 层断 SQL 字符串
 * Room 的 `runMigrationsAndValidate` 需要 instrumented 环境，但本迁移最容易坏的地方
 * 恰恰是「语句本身写没写对」，而这些都能在 JVM 断言：
 * - `initialBalance` 的 DEFAULT 写法（`0` vs `0.0`）—— 写错 Room 直接判 schema 不匹配，
 *   且**编译期看不出来**，只在真跑迁移时才爆。
 * - 可空新列有没有被误加 DEFAULT —— 加了反而 mismatch（`rrule` / `avatarUri` 的先例）。
 * - Entity 声明的新索引有没有建 —— 漏一个就是 missing index，启动即崩。
 * - 转账分类补齐是否覆盖**所有**账户且幂等 —— 漏了老库第一次转账就撞外键 RESTRICT。
 *
 * ## 局限（必须诚实）
 * 这些断言证明「语句存在且形状正确」，**不证明**「在真实 SQLite 上按序执行成功」。
 * 后者由 [com.example.personalassistant.core.database.AppDatabaseMigrationsTest]（androidTest）
 * 补，未真跑通过前端账不标 ✅。
 */
class Migration_9_10StatementsTest {

    private val statements = MIGRATION_9_10_STATEMENTS

    @Test
    fun `user_profiles 加 initialBalance 且 DEFAULT 逐字为 0`() {
        val alter = statements.single {
            it.startsWith("ALTER TABLE `user_profiles` ADD COLUMN `initialBalance`")
        }
        assertTrue("期初余额必须 NOT NULL（否则聚合要到处判空）：$alter", alter.contains("NOT NULL"))
        // ⚠️ 关键：SQLite 的 dflt_value 原文是 `0`，写成 `0.0` 与 Entity 的
        // @ColumnInfo(defaultValue = "0") 对不上，runMigrationsAndValidate 会红。
        assertTrue("DEFAULT 必须是裸 0，不能是 0.0：$alter", alter.endsWith("DEFAULT 0"))
        assertTrue("不应出现 DEFAULT 0.0：$alter", !alter.contains("DEFAULT 0.0"))
    }

    @Test
    fun `可空转账列不得声明 DEFAULT 值`() {
        // Room schema JSON 对「可空且无 DEFAULT」的列不写 defaultValue 键；
        // 一旦 SQL 里加了 DEFAULT，实测 dflt_value 就与 Entity 注解不一致。
        val out = statements.single {
            it.startsWith("ALTER TABLE `transactions` ADD COLUMN `transferAccountId`")
        }
        val pair = statements.single {
            it.startsWith("ALTER TABLE `transactions` ADD COLUMN `transferPairId`")
        }
        assertTrue("transferAccountId 应可空：$out", out.contains("INTEGER DEFAULT NULL"))
        assertTrue("transferAccountId 不应 NOT NULL：$out", !out.contains("NOT NULL"))
        assertTrue("transferPairId 应可空 TEXT：$pair", pair.contains("TEXT DEFAULT NULL"))
    }

    @Test
    fun `Entity 新增的 accountId_type 复合索引必须建立`() {
        // TransactionEntity 里加了 Index(["accountId","type"])；迁移不建，
        // Room 校验会报 missing index 直接启动失败。
        val idx = statements.single { it.contains("CREATE INDEX IF NOT EXISTS `index_transactions_accountId_type`") }
        assertTrue("索引应含两列且顺序为 (accountId, type)：$idx", idx.contains("(`accountId`, `type`)"))
        assertTrue("索引应建在 transactions 上：$idx", idx.contains("ON `transactions`"))
    }

    @Test
    fun `给每个存量账户补转账分类且幂等`() {
        val seed = statements.single { it.contains("INSERT INTO `categories`") }
        // 必须是 INSERT ... SELECT FROM user_profiles：一条语句覆盖全部账户。
        // 写死 VALUES 只塞默认账户的话，第二个账户转账会撞外键。
        assertTrue("应从 user_profiles 取全部账户：$seed", seed.contains("FROM `user_profiles`"))
        assertTrue("必须按账户关联写 accountId", seed.contains("up.`id`"))
        // 幂等守卫：重复跑 / 未来重放都安全
        assertTrue("必须带 NOT EXISTS 幂等守卫：$seed", seed.contains("WHERE NOT EXISTS"))
        assertTrue("幂等条件应按 accountId + type 判重", seed.contains("c.`accountId` = up.`id`"))
        assertTrue("幂等条件应锁转账分类 type", seed.contains("c.`type` = ${DefaultCategories.CATEGORY_TYPE_TRANSFER}"))
    }

    @Test
    fun `迁移 SQL 的分类值与 Kotlin seed 同源于常量`() {
        // 两处各写字面量的话，改一处忘一处不会报错，只会让老库与新装的转账分类悄悄不同。
        val seed = statements.single { it.contains("INSERT INTO `categories`") }
        assertTrue("名称应引用常量值", seed.contains("'${DefaultCategories.TRANSFER_CATEGORY_NAME}'"))
        assertTrue("图标应引用常量值", seed.contains("'${DefaultCategories.TRANSFER_ICON_NAME}'"))
        assertTrue("颜色应引用常量值", seed.contains("${DefaultCategories.TRANSFER_CATEGORY_COLOR}"))
        assertTrue("应标记为内置分类（isDefault=1，用户不可删）", seed.contains("1,\n"))
    }

    @Test
    fun `本轮是纯增量迁移不得重建任何表`() {
        // Migration_8_9 需要重建 transactions（单列 FK 升复合 FK，SQLite 不支持 ALTER FK）。
        // 本轮只加列与索引，一旦有人引入 DROP TABLE / 建新表搬数据，风险面立刻上升一个数量级，
        // 这条断言用来把「不该重建」这个决定钉住。
        assertTrue("不应 DROP 任何表", statements.none { it.contains("DROP TABLE") })
        assertTrue("不应创建新表", statements.none { it.contains("CREATE TABLE") })
        assertTrue("不应有表重命名", statements.none { it.contains("RENAME TO") })
    }

    @Test
    fun `补齐分类应排在加列与建索引之后`() {
        // 注释明写「依赖 categories 表结构未变，放最后最安全」；
        // 顺序若被重排，INSERT ... SELECT 可能在索引/列尚未就绪时执行。
        val lastAlter = statements.indexOfLast { it.contains("ADD COLUMN") }
        val createIdx = statements.indexOfFirst { it.contains("index_transactions_accountId_type") }
        val seedIdx = statements.indexOfFirst { it.contains("INSERT INTO `categories`") }
        assertTrue("加列应存在", lastAlter >= 0)
        assertTrue("建索引应在加列之后", createIdx > lastAlter)
        assertTrue("补分类应是最后一步", seedIdx > createIdx && seedIdx == statements.lastIndex)
    }

    @Test
    fun `迁移不应包含 PRAGMA foreign_keys 开关`() {
        // Room 把迁移包在事务里，事务内该 PRAGMA 是 no-op；写了只会误导后来者。
        // （对比：androidTest 里必须显式开启，因为那里拿的是裸连接、不走 Room 事务。）
        assertTrue(
            "不应出现 foreign_keys 开关（事务内无效）",
            statements.none { it.contains("PRAGMA foreign_keys") },
        )
    }

    @Test
    fun `转账分类常量与流水类型编号空间的实际关系`() {
        // 注释曾声称有一条测试锁「不重叠」—— 实际上 CATEGORY_TYPE_TRANSFER(2) 与
        // TRANSFER_OUT.storageValue(2) **数值相同**，只是落在 categories.type 与
        // transactions.type 两个不同列上，不冲突但极易误读。
        // 本断言把这个「同值不同列」的事实固定下来，防止有人以为改一个另一个会跟着变，
        // 也防止有人误把 categories.type 当成 TransactionType 的子集来做过滤。
        assertEquals(DefaultCategories.CATEGORY_TYPE_TRANSFER, TransactionTypeStable.TRANSFER_OUT_VALUE)
        assertEquals(2, DefaultCategories.CATEGORY_TYPE_TRANSFER)
        // 收支分类的 0/1 与流水的 0/1 同样同值不同列 —— 这是本项目既有的编号约定。
        assertEquals(0, TransactionTypeStable.EXPENSE_VALUE)
        assertEquals(1, TransactionTypeStable.INCOME_VALUE)
    }

    /**
     * 流水类型编号的稳定性锁。
     *
     * 不直接引 [com.example.personalassistant.feature.finance.domain.model.TransactionType]
     * 是为了避免 core.database 包的测试反向依赖 finance.domain；
     * 真正的枚举一致性在 TransactionTypeTransferTest 里断言。
     */
    private object TransactionTypeStable {
        const val EXPENSE_VALUE = 0
        const val INCOME_VALUE = 1
        const val TRANSFER_OUT_VALUE = 2
    }
}
