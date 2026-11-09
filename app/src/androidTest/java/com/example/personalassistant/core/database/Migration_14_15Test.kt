package com.example.personalassistant.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 16：Migration_14_15 真跑测试（清单模型合并）
 *
 * ## 这个文件验证什么（JVM 层做不到的部分）
 *
 * [Migration_12_13StatementsTest] 那类 JVM 测试只能断言「SQL 语句形状对」；
 * 这里验证「在真实 SQLite 上按序执行成功，且结果符合 Room schema 校验 + 数据语义正确」：
 *
 * 1. `helper.runMigrationsAndValidate(TEST_DB, 15, true, Migration_14_15)` —— 迁移跑通，
 *    且迁移后的实际表结构与导出的 15.json 完全一致（列 / 索引 / 外键任一不符即抛异常）
 * 2. 内置「收集箱」（`task_lists` id = 1）被建出来
 * 3. 原有 `tasks` 行全部落到收集箱（`listId = 1`），一条没丢
 * 4. 原 `sub_tasks` 的**独立顶层项**变成 `parentTaskId IS NULL` 的顶层任务
 * 5. 原 `sub_tasks` 的**任务子项**仍指向原任务（`parentTaskId` 保持原值）
 * 6. 原 `sub_tasks` 的**嵌套子项**指向搬迁后的父行（`parentTaskId = 旧父 id + offset`）
 * 7. 标签关联从 `sub_task_tag_cross_ref` 并入 `task_tag_cross_ref`（taskId 同样带偏移）
 * 8. `sub_tasks` / `sub_task_tag_cross_ref` 两张旧表已不存在
 *
 * ## 运行前提
 * 需要连接的设备或模拟器（`adb devices` 非空）。
 * 跑法：
 * ```
 * ./gradlew -Pandroid.testInstrumentationRunnerArguments.class=\
 *   com.example.personalassistant.core.database.Migration_14_15Test connectedDebugAndroidTest
 * ```
 *
 * ## 为什么测试名不用反引号空格写法
 * DEX 035（minSdk 26 对应的版本）不允许方法名含空格，会直接 `dexBuilderDebugAndroidTest FAILED`。
 * 所以仪表测试一律用 camelCase 方法名 + 注释描述意图。
 */
@RunWith(AndroidJUnit4::class)
class Migration_14_15Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * 14→15：schema 校验通过 + 数据搬迁语义正确。
     *
     * 造的旧数据覆盖了 `sub_tasks` 的三种形态：
     * - id=1：独立顶层项（`parentTaskId IS NULL` 且 `parentSubTaskId IS NULL`）
     * - id=2：任务子项（`parentTaskId = 1`，指向 `tasks` 行）
     * - id=3：嵌套子项（`parentSubTaskId = 1`，指向另一条 `sub_tasks` 行）
     */
    @Test
    fun migrate14To15ValidatesSchemaAndMovesSubTasksIntoTasks() {
        // ===== 1. 造一份 v14 结构的库，塞入「两套模型并存」的旧数据 =====
        val db14 = helper.createDatabase(TEST_DB, 14)
        db14.apply {
            // 原有任务（2 条）—— 迁移后应当 listId = 1（收集箱），parentTaskId 保持 NULL
            execSQL(
                "INSERT INTO tasks (id, title, priority, isCompleted, createdAt, updatedAt, holidayStrategy) " +
                    "VALUES (1, '旧任务A', 1, 0, 1000, 1000, 'SKIP')",
            )
            execSQL(
                "INSERT INTO tasks (id, title, priority, isCompleted, createdAt, updatedAt, holidayStrategy) " +
                    "VALUES (2, '旧任务B', 1, 0, 2000, 2000, 'SKIP')",
            )
            // 旧清单项 3 种形态
            execSQL(
                "INSERT INTO sub_tasks (id, parentTaskId, title, isCompleted, `order`, createdAt, " +
                    "category, dueDate, priority, amount, parentSubTaskId) " +
                    "VALUES (1, NULL, '独立顶层项', 0, 0, 3000, '生活', 5000, 2, 9.9, NULL)",
            )
            execSQL(
                "INSERT INTO sub_tasks (id, parentTaskId, title, isCompleted, `order`, createdAt, " +
                    "category, dueDate, priority, amount, parentSubTaskId) " +
                    "VALUES (2, 1, '任务子项', 0, 1, 4000, '工作', 6000, 1, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO sub_tasks (id, parentTaskId, title, isCompleted, `order`, createdAt, " +
                    "category, dueDate, priority, amount, parentSubTaskId) " +
                    "VALUES (3, NULL, '嵌套子项', 0, 2, 5000, '学习', 7000, 1, 1.5, 1)",
            )
            // 标签关联（旧表）
            execSQL("INSERT INTO tags (id, nameNormalized, displayName, colorHex, createdAt) " +
                "VALUES (7, 'work', 'Work', 0, 1000)")
            execSQL("INSERT INTO sub_task_tag_cross_ref (subTaskId, tagId) VALUES (1, 7)")
        }
        db14.close()

        // offset = MAX(tasks.id) = 2 → sub_tasks 的新 id = 旧 id + 2
        val offset = 2L

        // ===== 2. 跑迁移并向 Room 校验 schema（结构性错误在这里就会炸）=====
        val db15 = helper.runMigrationsAndValidate(TEST_DB, 15, true, Migration_14_15)

        // ===== 3. 内置收集箱已建出 =====
        db15.query("SELECT id, nameNormalized, isSystem FROM task_lists").use { c ->
            assertTrue("迁移应建出收集箱", c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("inbox", c.getString(1))
            assertEquals("收集箱应是系统清单", 1, c.getInt(2))
            assertTrue("只应有 1 个清单", !c.moveToNext())
        }

        // ===== 4. 原有 tasks 行全部落到收集箱，一条没丢 =====
        db15.query("SELECT COUNT(*) FROM tasks WHERE id IN (1, 2)").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("原有任务必须还在", 2, c.getInt(0))
        }
        db15.query("SELECT listId FROM tasks WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("原任务应落到收集箱", 1L, c.getLong(0))
        }

        // ===== 5. 独立顶层清单项 → 顶层任务（parentTaskId IS NULL）=====
        db15.query(
            "SELECT id, title, parentTaskId, listId, amount, category FROM tasks WHERE id = ?",
            arrayOf<Any>(offset + 1),
        ).use { c ->
            assertTrue("独立清单项必须搬进 tasks", c.moveToFirst())
            assertEquals("独立顶层项", c.getString(1))
            assertTrue("独立顶层项不应有父指针", c.isNull(2))
            assertEquals("应落到收集箱", 1L, c.getLong(3))
            assertEquals(9.9, c.getDouble(4), 0.0001)
            assertEquals("生活", c.getString(5))
        }

        // ===== 6. 任务子项仍指向原任务（parentTaskId 保持原值，不偏移）=====
        db15.query(
            "SELECT id, title, parentTaskId FROM tasks WHERE id = ?",
            arrayOf<Any>(offset + 2),
        ).use { c ->
            assertTrue("任务子项必须搬进 tasks", c.moveToFirst())
            assertEquals("任务子项", c.getString(1))
            assertEquals("任务子项应仍指向原任务 id=1", 1L, c.getLong(2))
        }

        // ===== 7. 嵌套子项指向搬迁后的父行（父 id + offset）=====
        db15.query(
            "SELECT id, title, parentTaskId FROM tasks WHERE id = ?",
            arrayOf<Any>(offset + 3),
        ).use { c ->
            assertTrue("嵌套子项必须搬进 tasks", c.moveToFirst())
            assertEquals("嵌套子项", c.getString(1))
            assertEquals(
                "嵌套子项应指向搬迁后的父行（1 + offset）",
                offset + 1,
                c.getLong(2),
            )
        }

        // ===== 8. 标签关联并入 task_tag_cross_ref（taskId 同样带偏移）=====
        db15.query("SELECT taskId, tagId FROM task_tag_cross_ref").use { c ->
            assertTrue("标签关联必须搬过来", c.moveToFirst())
            assertEquals("关联行的 taskId 应带偏移", offset + 1, c.getLong(0))
            assertEquals(7L, c.getLong(1))
            assertTrue("只应有 1 条关联", !c.moveToNext())
        }

        // ===== 9. 旧表已删除 =====
        assertFalse("sub_tasks 应已删除", tableExists(db15, "sub_tasks"))
        assertFalse("sub_task_tag_cross_ref 应已删除", tableExists(db15, "sub_task_tag_cross_ref"))

        // ===== 10. 总行数对得上：2 条原任务 + 3 条清单项 = 5 条 =====
        db15.query("SELECT COUNT(*) FROM tasks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("tasks 总行数应为 5（2 原任务 + 3 清单项）", 5, c.getInt(0))
        }

        db15.close()
    }

    /** 表是否存在（`sqlite_master` 查询）。 */
    private fun tableExists(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
    ): Boolean = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'",
    ).use { it.count > 0 }

    private companion object {
        const val TEST_DB = "migration-14-15-test"
    }
}
