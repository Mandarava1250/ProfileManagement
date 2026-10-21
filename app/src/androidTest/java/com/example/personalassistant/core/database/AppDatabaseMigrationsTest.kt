package com.example.personalassistant.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.personalassistant.feature.finance.data.seed.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 3（B 路线）：Migration_8_9 真跑测试
 *
 * ## 这个文件验证什么（JVM 层做不到的部分）
 * [Migration_8_9StatementsTest] 只能断言「SQL 语句形状对」；这里验证
 * 「在真实 SQLite 上按序执行成功且结果符合 Room schema 校验」：
 * 1. `helper.runMigrationsAndValidate(TEST_DB, 9, true, Migration_8_9)` —— 迁移跑通，
 *    且迁移后的实际表结构与导出的 9.json 完全一致（索引名/列/FK 任一不符即抛异常）
 * 2. 旧数据无损落到 accountId=1
 * 3. 两个账户可以各写同一天/同一个月（复合唯一索引真的生效）
 * 4. 旧的全局唯一索引已被替换（若没替换，第 3 步会撞 UNIQUE）
 * 5. 跨账户引用分类会被复合 FK 拒绝（需先 PRAGMA foreign_keys=ON，见测试内注释）
 *
 * ## 运行前提（2026-09-12 已在本机配好）
 * 需要连接的设备或模拟器：
 *   adb devices 非空 / 或先装 system-images + 建 AVD
 *   本机已配：AVD `pm_api34`（aosp_atd / x86_64 / API 34，WHPX 硬件加速）
 *   启动：emulator -avd pm_api34 -no-window -no-audio -no-boot-anim -read-only
 * 跑法：
 *   ./gradlew connectedDebugAndroidTest
 * 若只有真机/模拟器可用，也可：
 *   ./gradlew -Pandroid.testInstrumentationRunnerArguments.class=\
 *     com.example.personalassistant.core.database.AppDatabaseMigrationsTest connectedDebugAndroidTest
 *
 * ## 为什么用 MigrationTestHelper(context, AppDatabase::class.java) 而不是 createMigrationTestDriver
 * Room 导出的 schema 目录是**数据库类全名**（app/schemas/com.example...AppDatabase/8.json），
 * 而 createMigrationTestDriver 按**数据库文件名**找 assets（personal_assistant.db/8.json），
 * 两者对不上会报「Could not find database schema」。2.6.1 的类名重载才是正确用法。
 *
 * 另一细节：createDatabase 返回的是 [androidx.sqlite.db.SupportSQLiteDatabase]，
 * 只有 `query(sql)`，没有 android 特有的 `rawQuery(sql, args)` —— 别照 Activity 层的 API 写。
 *
 * ## 为什么测试名不用反引号空格写法
 * `fun \`migrate 8 to 9 ...\`() 这种名字在 **JVM 单测**里完全合法，所以 Phase 3 写这个文件时
 * 只跑了 `compileDebugAndroidTestKotlin`（绿）就以为没问题。但 androidTest 还要过 **D8 dex**：
 *   D8: Space characters in SimpleName '...' are not allowed prior to DEX version 040
 * DEX 035（minSdk 26 对应的版本）不允许方法名含空格，直接 `dexBuilderDebugAndroidTest FAILED`。
 * 所以仪表测试一律用 camelCase 方法名 + 注释描述意图；空格名只留给 src/test 下的 JVM 测试。
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationsTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /** 8→9：schema 校验通过，旧行全部落到默认账户 */
    @Test
    fun migrate8To9ValidatesSchemaAndKeepsLegacyRowsOnDefaultAccount() {
        // ===== 1. 先造一份 v8 结构的库，并塞入「旧时代」数据（表还没有 accountId 列）=====
        val db8 = helper.createDatabase(TEST_DB, 8)
        db8.apply {
            execSQL(
                "INSERT INTO categories (id, name, type, iconName, color, isDefault, sortOrder) " +
                    "VALUES (1, '餐饮', 0, 'Restaurant', -1000, 1, 0)",
            )
            execSQL(
                "INSERT INTO transactions (id, amount, type, categoryId, date, note, createdAt, source) " +
                    "VALUES (1, 12.5, 0, 1, 1000, '旧账', 1001, '手动')",
            )
            execSQL(
                "INSERT INTO monthly_budgets (id, yearMonth, totalAmount, isAutoRefresh, carryOverEnabled, warningThreshold, createdAt, updatedAt) " +
                    "VALUES (1, '2026-08', 900.0, 1, 1, 0.0, 1002, 1002)",
            )
            execSQL(
                "INSERT INTO daily_pools (id, date, dailyBudget, remainingAmount, totalSpentToday, totalIncomeToday, carryOver, lastUpdated) " +
                    "VALUES (1, 1000, 30.0, 12.0, 18.0, 0.0, 0.0, 1003)",
            )
        }
        db8.close()

        // ===== 2. 跑迁移并向 Room 校验 schema（结构性错误在这里就会炸）=====
        val db9 = helper.runMigrationsAndValidate(TEST_DB, 9, true, Migration_8_9)

        // ===== 3. 默认账户已被建出来 =====
        db9.query("SELECT id, name FROM user_profiles").use { c ->
            assertTrue("迁移应建出默认账户", c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("默认账户", c.getString(1))
            assertTrue("只应有 1 个账户", !c.moveToNext())
        }

        // ===== 4. 旧数据无损落在 accountId=1（一条都没丢）=====
        db9.query(
            "SELECT accountId, amount, note FROM transactions WHERE id = 1",
        ).use { c ->
            assertTrue("旧流水必须还在", c.moveToFirst())
            assertEquals("旧数据必须落到默认账户", 1L, c.getLong(0))
            assertEquals(12.5, c.getDouble(1), 0.0001)
            assertEquals("旧账", c.getString(2))
        }
        assertEquals(1, countRows(db9, "categories"))
        assertEquals(1, countRows(db9, "monthly_budgets"))
        assertEquals(1, countRows(db9, "daily_pools"))

        // ===== 5. 复合唯一索引生效：两个账户可以各写同一天 / 同一个月 =====
        db9.execSQL(
            "INSERT INTO categories (id, name, type, iconName, color, isDefault, sortOrder, accountId) " +
                "VALUES (2, '餐饮', 0, 'Restaurant', -1000, 1, 0, 2)",
        )
        db9.execSQL(
            "INSERT INTO daily_pools (id, accountId, date, dailyBudget, remainingAmount, totalSpentToday, totalIncomeToday, carryOver, lastUpdated) " +
                "VALUES (2, 1, 2000, 30.0, 30.0, 0.0, 0.0, 0.0, 1)",
        )
        // 第二个账户的同一天：旧索引（date 全局唯一）没被 DROP 的话，这里会抛 UNIQUE constraint failed
        db9.execSQL(
            "INSERT INTO daily_pools (id, accountId, date, dailyBudget, remainingAmount, totalSpentToday, totalIncomeToday, carryOver, lastUpdated) " +
                "VALUES (3, 2, 2000, 50.0, 50.0, 0.0, 0.0, 0.0, 1)",
        )
        db9.execSQL(
            "INSERT INTO monthly_budgets (id, accountId, yearMonth, totalAmount, isAutoRefresh, carryOverEnabled, warningThreshold, createdAt, updatedAt) " +
                "VALUES (2, 1, '2026-09', 800.0, 1, 1, 0.0, 1, 1)",
        )
        db9.execSQL(
            "INSERT INTO monthly_budgets (id, accountId, yearMonth, totalAmount, isAutoRefresh, carryOverEnabled, warningThreshold, createdAt, updatedAt) " +
                "VALUES (3, 2, '2026-09', 700.0, 1, 1, 0.0, 1, 1)",
        )
        assertEquals(3, countRows(db9, "daily_pools"))
        assertEquals(3, countRows(db9, "monthly_budgets"))

        // 同账户同一天仍必须被拒（复合索引是「按账户唯一」，不是「不唯一」）
        assertTrue(
            "同账户同一天应撞 UNIQUE",
            runsThrowing {
                db9.execSQL(
                    "INSERT INTO daily_pools (id, accountId, date, dailyBudget, remainingAmount, totalSpentToday, totalIncomeToday, carryOver, lastUpdated) " +
                        "VALUES (4, 1, 2000, 1.0, 1.0, 0.0, 0.0, 0.0, 1)",
                )
            },
        )

        // ===== 6. 复合 FK 生效：账户 1 的流水不能引用账户 2 的分类 =====
        //
        // ⚠️ 必须先显式开启外键强制：Android 的 SQLiteDatabase **默认不开** foreign_keys（为了向后兼容），
        // MigrationTestHelper 返回的是裸连接，不像 Room 的 RoomOpenHelper 会自己开。
        // 首次真跑就是因为没开这一句，跨账户插入没被拒而失败（而不是 FK 写错）。
        // 另：PRAGMA foreign_keys 在事务内是 no-op，必须放在迁移事务已提交之后的现在。
        db9.execSQL("PRAGMA foreign_keys = ON")
        assertEquals(
            "外键强制应已开启（否则下面两条断言会失真）",
            1,
            foreignKeysEnabled(db9),
        )

        assertTrue(
            "跨账户引用分类应被复合 FK 拒绝",
            runsThrowing {
                db9.execSQL(
                    "INSERT INTO transactions (id, accountId, amount, type, categoryId, date, createdAt, source) " +
                        "VALUES (10, 1, 5.0, 0, 2, 3000, 1, '手动')",
                )
            },
        )
        // 同账户引用则必须成功（否则就是 FK 写错方向，误伤了正常路径）
        db9.execSQL(
            "INSERT INTO transactions (id, accountId, amount, type, categoryId, date, createdAt, source) " +
                "VALUES (11, 2, 5.0, 0, 2, 3000, 1, '手动')",
        )
        assertEquals(2, countRows(db9, "transactions"))

        db9.close()
    }

    /** 8→9：空库（无 sqlite_sequence）也能安全迁移 */
    @Test
    fun migrationIsSafeForFreshV8DatabaseWithoutAnyTransactions() {
        // 回归「装了但从未记账」的设备：sqlite_sequence 表还不存在。
        // 若那两条 UPDATE 混在无条件语句列表里，这里会抛 no such table: sqlite_sequence。
        val db8 = helper.createDatabase(TEST_DB, 8)
        db8.close()

        val db9 = helper.runMigrationsAndValidate(TEST_DB, 9, true, Migration_8_9)
        assertEquals(1, countRows(db9, "user_profiles"))
        assertEquals(0, countRows(db9, "transactions"))
        db9.close()
    }

    /**
     * 9→10：跨账户转账 + 账户余额（Phase 5）
     *
     * 验证 [Migration_9_10] 在真实 SQLite 上按序执行成功，结果符合 v10 Room schema：
     * 1. `helper.runMigrationsAndValidate(TEST_DB, 10, true, Migration_9_10)` —— 迁移跑通，
     *    且迁移后的实际表结构与导出的 10.json 完全一致（索引 / 列 / FK 任一不符即抛异常）
     * 2. `user_profiles.initialBalance` 列存在，旧账户默认 0
     * 3. `transactions.transferAccountId` / `transferPairId` 列存在，旧流水默认值 NULL
     * 4. 索引 `index_transactions_accountId_type` 已建立（Entity 声明，迁移漏建会报 missing index）
     * 5. 默认账户被补了一条 type=2 的「转账」分类（引用 [DefaultCategories] 常量）
     * 6. PRAGMA foreign_key_check 收尾守卫无违规（迁移代码内置）
     *
     * ⚠️ **plan §4.3 子项 8c.1 的描述是 stale 的**：
     * plan 文件原话说"events.category String → events.category_id Long"——实际 [Migration_9_10] 是
     * 「跨账户转账 + 账户余额」（Phase 5 工作产物，commit 535c73d）。这是 plan 与实现脱节的典型
     * 例子，与扩展 A 中记录的「Phase 8b 真实完成度盘点」是同款问题。下次写 plan 必须先看真实代码。
     */
    @Test
    fun migrate9To10ValidatesSchemaAndAddsTransferFields() {
        // ===== 1. 造一份 v9 结构的库（用 9.json 引导），并塞入「旧时代」数据 =====
        // v9 的表结构（实测 9.json）：
        // - user_profiles: (id, name, avatarUri, createdAt) —— 没有 initialBalance
        // - categories:    (id, accountId, name, type, iconName, color, isDefault, sortOrder) —— 没有 type=2 转账分类
        // - transactions:  (id, accountId, amount, type, categoryId, date, note, createdAt, source, attachmentUri)
        //                   —— 没有 transferAccountId / transferPairId
        //                   —— 没有 index_transactions_accountId_type
        val db9 = helper.createDatabase(TEST_DB, 9)
        db9.apply {
            // 默认账户（id=1），v9 时代还没有 initialBalance
            execSQL(
                "INSERT INTO `user_profiles` (`id`, `name`, `createdAt`) VALUES (1, '默认账户', 1000)",
            )
            // 一条支出分类（type=0）+ 一条收入分类（type=1），v9 时代不含 type=2
            execSQL(
                "INSERT INTO `categories` (`id`, `accountId`, `name`, `type`, `iconName`, `color`, `isDefault`, `sortOrder`) " +
                    "VALUES (1, 1, '餐饮', 0, 'Restaurant', -1000, 1, 0)",
            )
            execSQL(
                "INSERT INTO `categories` (`id`, `accountId`, `name`, `type`, `iconName`, `color`, `isDefault`, `sortOrder`) " +
                    "VALUES (2, 1, '工资', 1, 'AccountBalanceWallet', -16711936, 1, 0)",
            )
            // 一笔 v9 时代的支出流水（无 transferAccountId / transferPairId）
            execSQL(
                "INSERT INTO `transactions` " +
                    "(`id`, `accountId`, `amount`, `type`, `categoryId`, `date`, `note`, `createdAt`, `source`) " +
                    "VALUES (1, 1, 12.5, 0, 1, 1000, 'v9 旧账', 1001, '手动')",
            )
        }
        db9.close()

        // ===== 2. 跑迁移并向 Room 校验 schema（结构性错误在这里就会炸）=====
        val db10 = helper.runMigrationsAndValidate(TEST_DB, 10, true, Migration_9_10)

        // ===== 3. user_profiles.initialBalance 已被加上，旧账户默认 0 =====
        db10.query("SELECT initialBalance FROM user_profiles WHERE id = 1").use { c ->
            assertTrue("迁移应保留旧账户", c.moveToFirst())
            assertEquals("v9 时代没有初始余额，应为 DEFAULT 0", 0.0, c.getDouble(0), 0.0001)
        }

        // ===== 4. 旧 transactions 的 transferAccountId / transferPairId 都是 NULL =====
        db10.query("SELECT transferAccountId, transferPairId FROM transactions WHERE id = 1").use { c ->
            assertTrue("旧流水必须还在", c.moveToFirst())
            assertTrue("transferAccountId 应 NULL", c.isNull(0))
            assertTrue("transferPairId 应 NULL", c.isNull(1))
        }

        // ===== 5. 索引 index_transactions_accountId_type 存在 =====
        db10.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_transactions_accountId_type'",
        ).use { c ->
            assertTrue("Entity 声明的复合索引必须建立（漏建会启动崩）", c.moveToFirst())
            assertEquals("index_transactions_accountId_type", c.getString(0))
        }

        // ===== 6. 默认账户被补一条 type=2 转账分类（来自 DefaultCategories 常量）=====
        db10.query(
            "SELECT name, type, accountId, isDefault FROM categories WHERE type = ? AND accountId = 1",
            arrayOf<Any>(DefaultCategories.CATEGORY_TYPE_TRANSFER),
        ).use { c ->
            assertTrue("转账分类必须存在（漏补老库第一次转账就撞外键）", c.moveToFirst())
            assertEquals(
                "分类名应来自 DefaultCategories.TRANSFER_CATEGORY_NAME",
                DefaultCategories.TRANSFER_CATEGORY_NAME,
                c.getString(0),
            )
            assertEquals(DefaultCategories.CATEGORY_TYPE_TRANSFER, c.getInt(1))
            assertEquals(1L, c.getLong(2))
            assertEquals("应标记为内置分类（isDefault=1，用户不可删）", 1, c.getInt(3))
            assertTrue("只该有一条转账分类", !c.moveToNext())
        }

        // ===== 7. 旧的两条收支分类没被改动 =====
        assertEquals(3, countRows(db10, "categories"))
        assertEquals(1, countRows(db10, "transactions"))

        db10.close()
    }

    /** 9→10：空 v9 库（无任何用户/分类/流水）也能安全迁移 */
    @Test
    fun migrate9To10IsSafeForFreshV9DatabaseWithoutAnyTransfer() {
        // MigrationTestHelper 用 9.json 重建库，**不会**调用 RoomDatabase.Callback.seed。
        // 所以 v9 库是空的，没有任何 user_profiles / categories / transactions。
        // 9→10 迁移的最后一步是 INSERT ... SELECT FROM user_proprofiles —— 空表不会执行插入（0 行受影响），
        // 也不会炸：只是不会补任何转账分类。**这是正常路径**：用户应用第一次启动走 onCreate 路径，
        // 由 Phase 3 的 seed 逻辑创建默认账户，不靠迁移补。
        // 本测试只回归「空库跑迁移不抛异常」这一件事。
        val db9 = helper.createDatabase(TEST_DB, 9)
        db9.close()

        val db10 = helper.runMigrationsAndValidate(TEST_DB, 10, true, Migration_9_10)
        assertEquals("空库无 user_profiles", 0, countRows(db10, "user_profiles"))
        assertEquals(0, countRows(db10, "transactions"))
        assertEquals("空库无 categories（9→10 不会自动 seed）", 0, countRows(db10, "categories"))
        db10.close()
    }

    /**
     * 10→11→12：**从 v10 起跑全链**（真实升级路径）
     *
     * 这是线上闪退的**决定性回归守卫**。
     *
     * ## 为什么要从 v10 起，而不是 v11
     *
     * `student_profile` 表是 [Migration_10_11] **手写 CREATE TABLE** 建出来的：
     * ```sql
     * `gpaTarget`  REAL NOT NULL DEFAULT 4.0,
     * `resumeText` TEXT NOT NULL DEFAULT '',
     * ```
     * 而 `gpaRuleJson` 是 [Migration_11_12] 用 `ALTER TABLE ADD COLUMN` 加的
     * （SQLite 的 ADD COLUMN 不附带 NOT NULL → 该列天生 nullable）。
     *
     * 如果只从 v11 起跑（`createDatabase(TEST_DB, 11)` 用 11.json 建表），
     * 11.json 里的 student_profile 是**Room 自己生成的形状**（无 DEFAULT），
     * Migration_10_11 根本没执行 → gpaTarget/resumeText 的 DEFAULT 差异测不出来。
     * 这正是之前那版测试漏掉两列的原因。
     *
     * ## 这个测试验证什么
     *
     * 1. `runMigrationsAndValidate(TEST_DB, 12, true, Migration_10_11, Migration_11_12)`
     *    —— Room **逐表逐列**把迁移后的实际表与 12.json 比对，
     *    任何一列的 notNull / defaultValue / type 不符就抛 IllegalStateException，
     *    **与线上闪退的栈完全一致**
     * 2. PRAGMA table_info 逐列断言 student_profile 三列的 notNull 与 dflt_value
     *
     * 线上闪退实测（Expected = Room 期望，Found = SQLite 实际）：
     * ```
     * gpaTarget   Expected dflt='undefined'  Found dflt='4.0'   → 不匹配
     * resumeText  Expected dflt='undefined'  Found dflt=''      → 不匹配
     * gpaRuleJson Expected notNull=true     Found notNull=false → 不匹配
     * ```
     * 三列都要对齐，漏一列照样闪退。
     */
    @Test
    fun migrate10To12ValidatesSchemaAcrossFullChain() {
        // ===== 1. 造一份 v10 结构的库（10.json 里还没有 student_profile 表）=====
        val db10 = helper.createDatabase(TEST_DB, 10)
        db10.close()

        // ===== 2. 跑 10→11→12 全链，Room 逐表逐列校验 =====
        val db12 = helper.runMigrationsAndValidate(
            TEST_DB, 12, true, Migration_10_11, Migration_11_12,
        )

        // ===== 3. 逐列断言 student_profile 的三列 =====
        val columns = mutableMapOf<String, Pair<Int, String?>>() // name -> (notNull, dflt_value)
        db12.query("PRAGMA table_info(`student_profile`)").use { c ->
            while (c.moveToNext()) {
                columns[c.getString(1)] = c.getInt(3) to c.getString(4)
            }
        }

        // gpaTarget：Migration_10_11 建的是 `REAL NOT NULL DEFAULT 4.0`
        assertTrue("gpaTarget 列必须存在", columns.containsKey("gpaTarget"))
        assertEquals("gpaTarget 应为 NOT NULL", 1, columns.getValue("gpaTarget").first)
        assertEquals("gpaTarget defaultValue 必须为 4.0", "4.0", columns.getValue("gpaTarget").second)

        // resumeText：Migration_10_11 建的是 `TEXT NOT NULL DEFAULT ''`
        assertTrue("resumeText 列必须存在", columns.containsKey("resumeText"))
        assertEquals("resumeText 应为 NOT NULL", 1, columns.getValue("resumeText").first)
        assertEquals("resumeText defaultValue 必须为 ''", "''", columns.getValue("resumeText").second)

        // gpaRuleJson：ADD COLUMN 出来的，**nullable** + DEFAULT ''
        assertTrue("gpaRuleJson 列必须存在", columns.containsKey("gpaRuleJson"))
        assertEquals(
            "gpaRuleJson 是 ADD COLUMN 加的，物理列必然 nullable（notNull=0）",
            0,
            columns.getValue("gpaRuleJson").first,
        )
        assertEquals("gpaRuleJson defaultValue 必须为 ''", "''", columns.getValue("gpaRuleJson").second)

        db12.close()
    }

    /** 11→12 单独跑：只加 gpaRuleJson 一列 */
    @Test
    fun migrate11To12ValidatesSchemaAndAddsGpaRuleJson() {
        // v11 的 student_profile（实测 11.json）：(accountId, gpaTarget, resumeText, updatedAt)
        // —— 没有 gpaRuleJson
        val db11 = helper.createDatabase(TEST_DB, 11)
        db11.close()

        // runMigrationsAndValidate 内部会读 12.json 与迁移后的实际表比对
        val db12 = helper.runMigrationsAndValidate(TEST_DB, 12, true, Migration_11_12)

        // PRAGMA table_info 验证 gpaRuleJson 列定义
        db12.query("PRAGMA table_info(`student_profile`)").use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(1) == "gpaRuleJson") {
                    found = true
                    // notnull: 0 —— ADD COLUMN 出来的列天生 nullable
                    assertEquals(
                        "gpaRuleJson 由 ADD COLUMN 加出，物理列必然 nullable",
                        0,
                        c.getInt(3),
                    )
                    // dflt_value: '' —— Room 期望与 SQLite 实际一致（闪退修法）
                    assertEquals(
                        "gpaRuleJson defaultValue 必须为 ''",
                        "''",
                        c.getString(4),
                    )
                }
            }
            assertTrue("gpaRuleJson 列必须存在", found)
        }

        db12.close()
    }

    // ===== helpers =====

    private fun foreignKeysEnabled(db: androidx.sqlite.db.SupportSQLiteDatabase): Int =
        db.query("PRAGMA foreign_keys").use { c ->
            if (c.moveToFirst()) c.getInt(0) else -1
        }

    private fun countRows(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { c ->
            if (c.moveToFirst()) c.getInt(0) else -1
        }

    private fun runsThrowing(block: () -> Unit): Boolean =
        try {
            block()
            false
        } catch (t: Throwable) {
            true
        }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
