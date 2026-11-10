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
 * Phase 28 P10：Migration_20_21 真跑测试（课表「教师 / 地点下沉到课时」）
 *
 * ## 这个文件验证什么（JVM 层做不到的部分）
 *
 * [Migration_20_21] 的语句形状有 JVM 单测守着，但「在真实 SQLite 上按序执行成功，
 * 且结果符合 Room schema 校验 + 数据语义正确」只能在设备上验：
 *
 * 1. `helper.runMigrationsAndValidate(TEST_DB, 21, true, MIGRATION_20_21)` —— 迁移跑通，
 *    且迁移后的实际表结构与导出的 21.json 完全一致
 *    （**两列的 DEFAULT 必须与实体的 `defaultValue = "''"` 逐字一致**，否则这里就抛异常）
 * 2. 回填生效：存量课时的 teacher / location = 课程级那份
 * 3. 回填后**课时数 = 课程数**（一条课时都不多、不少）
 *
 * ## 运行前提
 * 需要连接的设备或模拟器（`adb devices` 非空）。
 * 跑法：
 * ```
 * ./gradlew -Pandroid.testInstrumentationRunnerArguments.class=\
 *   com.example.personalassistant.core.database.Migration_20_21Test connectedDebugAndroidTest
 * ```
 *
 * ## 为什么测试名不用反引号空格写法
 * DEX 035（minSdk 26 对应的版本）不允许方法名含空格，会直接 `dexBuilderDebugAndroidTest FAILED`。
 * 所以仪表测试一律用 camelCase 方法名 + 注释描述意图。
 */
@RunWith(AndroidJUnit4::class)
class Migration_20_21Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /**
     * 20→21：两列加上了 + 回填生效 + 课时数与课程数一致。
     */
    @Test
    fun migrate20To21AddsColumnsAndBackfillsTeacherLocation() {
        // ===== 1. 造一份 v20 结构的库，塞入存量课程与课时 =====
        val db20 = helper.createDatabase(TEST_DB, 20)
        db20.apply {
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
            // v20 的 course_sessions 没有 teacher / location 两列
            execSQL(
                "INSERT INTO course_sessions (courseId, sortOrder, dayOfWeek, startTime, endTime) " +
                    "VALUES (1, 0, 1, 480, 525)",
            )
            execSQL(
                "INSERT INTO course_sessions (courseId, sortOrder, dayOfWeek, startTime, endTime) " +
                    "VALUES (1, 1, 3, 600, 645)",
            )
            execSQL(
                "INSERT INTO course_sessions (courseId, sortOrder, dayOfWeek, startTime, endTime) " +
                    "VALUES (2, 0, 3, 810, 855)",
            )
        }
        db20.close()

        // ===== 2. 跑迁移并向 Room 校验 schema（结构性错误在这里就会炸）=====
        val db21 = helper.runMigrationsAndValidate(TEST_DB, 21, true, MIGRATION_20_21)

        // ===== 3. 两列加上了 =====
        db21.query("PRAGMA table_info(course_sessions)").use { c ->
            var teacherColumn: String? = null
            var teacherDefault: String? = null
            var locationColumn: String? = null
            var locationDefault: String? = null
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndexOrThrow("name"))
                val dflt = c.getString(c.getColumnIndexOrThrow("dflt_value"))
                when (name) {
                    "teacher" -> {
                        teacherColumn = name
                        teacherDefault = dflt
                    }
                    "location" -> {
                        locationColumn = name
                        locationDefault = dflt
                    }
                }
            }
            assertEquals("teacher 列应已加出", "teacher", teacherColumn)
            assertEquals("location 列应已加出", "location", locationColumn)
            // Room 拿 PRAGMA 的 dflt_value 与实体的 defaultValue 比对 —— 不一致就判 schema 不匹配
            assertEquals("teacher 的 DEFAULT 必须逐字等于实体的 defaultValue", "''", teacherDefault)
            assertEquals("location 的 DEFAULT 必须逐字等于实体的 defaultValue", "''", locationDefault)
        }

        // ===== 4. 回填生效：每条课时的教师 / 地点 = 课程级那份 =====
        db21.query(
            "SELECT s.courseId, s.teacher, s.location FROM course_sessions s ORDER BY s.courseId, s.sortOrder",
        ).use { c ->
            assertTrue("应有 3 条课时", c.moveToFirst())
            assertEquals(1L, c.getLong(0))
            assertEquals("课时 1 的教师应回填成课程级那份", "李老师", c.getString(1))
            assertEquals("课时 1 的地点应回填成课程级那份", "教三 201", c.getString(2))

            assertTrue(c.moveToNext())
            assertEquals(1L, c.getLong(0))
            assertEquals("同门课的第 2 个课时也要回填", "李老师", c.getString(1))
            assertEquals("同门课的第 2 个课时也要回填", "教三 201", c.getString(2))

            assertTrue(c.moveToNext())
            assertEquals(2L, c.getLong(0))
            assertEquals("另一门课的教师也要回填成它自己的", "王老师", c.getString(1))
            assertEquals("另一门课的地点也要回填成它自己的", "操场", c.getString(2))

            assertTrue("不该多出课时", !c.moveToNext())
        }

        // ===== 5. 回填后课时数与课程数一致（一条不多一条不少）=====
        db21.query("SELECT COUNT(*) FROM course_sessions").use { c ->
            assertTrue(c.moveToFirst())
            val sessionCount = c.getInt(0)
            db21.query("SELECT COUNT(*) FROM courses").use { c2 ->
                assertTrue(c2.moveToFirst())
                assertEquals("课时数应与课程数一致", c2.getInt(0), sessionCount)
            }
        }

        db21.close()
    }

    private companion object {
        const val TEST_DB = "migration-20-21-test"
    }
}
