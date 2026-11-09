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
 * Phase 28 P1：Migration_19_20 真跑测试（课表「一门课多课时」拆表）
 *
 * ## 这个文件验证什么（JVM 层做不到的部分）
 *
 * [Migration_19_20] 的语句形状有 JVM 单测守着，但「在真实 SQLite 上按序执行成功，
 * 且结果符合 Room schema 校验 + 数据语义正确」只能在设备上验：
 *
 * 1. `helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)` —— 迁移跑通，
 *    且迁移后的实际表结构与导出的 20.json 完全一致（列 / 索引 / 外键任一不符即抛异常）
 * 2. 存量 `courses` 每门课被播种成**一条**课时（sortOrder = 0，时间 = 主时段镜像）
 * 3. 外键 CASCADE 生效：删掉一门课 → 它的课时跟着被删掉
 *
 * ## 运行前提
 * 需要连接的设备或模拟器（`adb devices` 非空）。
 * 跑法：
 * ```
 * ./gradlew -Pandroid.testInstrumentationRunnerArguments.class=\
 *   com.example.personalassistant.core.database.Migration_19_20Test connectedDebugAndroidTest
 * ```
 *
 * ## 为什么测试名不用反引号空格写法
 * DEX 035（minSdk 26 对应的版本）不允许方法名含空格，会直接 `dexBuilderDebugAndroidTest FAILED`。
 * 所以仪表测试一律用 camelCase 方法名 + 注释描述意图。
 */
@RunWith(AndroidJUnit4::class)
class Migration_19_20Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * 19→20：表建出来 + 存量课程播种成课时 + 外键 CASCADE 生效。
     */
    @Test
    fun migrate19To20CreatesCourseSessionsAndCascadesDelete() {
        // ===== 1. 造一份 v19 结构的库，塞入存量课程 =====
        val db19 = helper.createDatabase(TEST_DB, 19)
        db19.apply {
            execSQL(
                "INSERT INTO courses (id, name, teacher, location, dayOfWeek, startTime, endTime, " +
                    "color, semester, weeks, reminderMinutes, note, credit, gradePoint, createdAt, updatedAt) " +
                    "VALUES (1, '高等数学 A', '李老师', '教三 201', 1, 480, 525, 4280391411, " +
                    "NULL, '1-16', 15, NULL, 4.0, 3.9, 1000, 1000)",
            )
            execSQL(
                "INSERT INTO courses (id, name, teacher, location, dayOfWeek, startTime, endTime, " +
                    "color, semester, weeks, reminderMinutes, note, credit, gradePoint, createdAt, updatedAt) " +
                    "VALUES (2, '体育', '王老师', '操场', 3, 810, 855, 4280391411, " +
                    "NULL, NULL, 15, NULL, 1.0, 3.5, 2000, 2000)",
            )
        }
        db19.close()

        // ===== 2. 跑迁移并向 Room 校验 schema（结构性错误在这里就会炸）=====
        val db20 = helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)

        // ===== 3. 课时表建出来了 =====
        assertTrue(
            "course_sessions 表应已建出",
            tableExists(db20, "course_sessions"),
        )
        db20.query("SELECT COUNT(*) FROM course_sessions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("存量 2 门课 → 播种 2 条课时", 2, c.getInt(0))
        }

        // ===== 4. 每门课一条课时，内容 = 主时段镜像 =====
        db20.query(
            "SELECT courseId, sortOrder, dayOfWeek, startTime, endTime FROM course_sessions " +
                "WHERE courseId = 1",
        ).use { c ->
            assertTrue("课程 1 应有一条课时", c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("播种的课时 sortOrder = 0", 0, c.getInt(1))
            assertEquals("课时星期几 = 主时段镜像", 1, c.getInt(2))
            assertEquals("课时开始时间 = 主时段镜像", 480, c.getInt(3))
            assertEquals("课时结束时间 = 主时段镜像", 525, c.getInt(4))
            assertTrue("课程 1 只应有一条课时", !c.moveToNext())
        }
        db20.query(
            "SELECT courseId, dayOfWeek, startTime FROM course_sessions WHERE courseId = 2",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2L, c.getLong(0))
            assertEquals(3, c.getInt(1))
            assertEquals(810, c.getInt(2))
        }

        // ===== 5. 外键 CASCADE：删课连带删课时 =====
        // Room 正常打开库时会打开外键约束；这里显式打开一次（迁移测试库是裸 SQL 打开的）
        db20.setForeignKeyConstraintsEnabled(true)
        db20.execSQL("DELETE FROM courses WHERE id = 1")

        db20.query("SELECT COUNT(*) FROM course_sessions WHERE courseId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("删课应连带删掉它的课时（CASCADE）", 0, c.getInt(0))
        }
        db20.query("SELECT COUNT(*) FROM course_sessions WHERE courseId = 2").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("别的课的课时不该被动到", 1, c.getInt(0))
        }
        db20.query("SELECT COUNT(*) FROM courses WHERE id = 2").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("别的课不该被删", 1, c.getInt(0))
        }

        db20.close()
    }

    /** 表是否存在（`sqlite_master` 查询）。 */
    private fun tableExists(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
    ): Boolean = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$tableName'",
    ).use { it.count > 0 }

    private companion object {
        const val TEST_DB = "migration-19-20-test"
    }
}
