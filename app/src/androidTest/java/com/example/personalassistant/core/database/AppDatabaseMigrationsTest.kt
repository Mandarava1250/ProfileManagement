package com.example.personalassistant.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
