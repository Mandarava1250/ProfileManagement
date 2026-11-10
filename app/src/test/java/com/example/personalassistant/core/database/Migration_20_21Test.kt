package com.example.personalassistant.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28 P10：Migration 20 → 21 的 DDL 断言
 *
 * 为什么要测这个：Room 在**运行期**才校验「迁移出来的表」与「Entity 声明的表」是否一致，
 * 编译期看不出来 —— DDL 写错一个字符就是线上用户一升级就崩。
 * 所以把关键片段断言在 JVM 单测里。
 *
 * 重点是两条 `ALTER TABLE` **必须带 DEFAULT**：
 * - SQLite 不允许给已有表加「NOT NULL 且无默认值」的列
 * - Room 2.6 的列比对是双向的：数据库有 DEFAULT 而实体没声明，同样算不匹配
 */
class Migration_20_21Test {

    @Test
    fun `给课时表加 teacher 列`() {
        val sql = migration20To21Statements().first { it.contains("ADD COLUMN `teacher`") }
        assertTrue(sql.contains("ALTER TABLE `course_sessions`"))
        assertTrue(sql.contains("`teacher` TEXT NOT NULL"))
    }

    @Test
    fun `给课时表加 location 列`() {
        val sql = migration20To21Statements().first { it.contains("ADD COLUMN `location`") }
        assertTrue(sql.contains("ALTER TABLE `course_sessions`"))
        assertTrue(sql.contains("`location` TEXT NOT NULL"))
    }

    @Test
    fun `两条 ALTER 都必须带 DEFAULT`() {
        val alters = migration20To21Statements().filter { it.contains("ALTER TABLE") }
        assertEquals(2, alters.size)
        alters.forEach {
            assertTrue(
                "ALTER TABLE 必须带 DEFAULT（Room 运行期会判列不一致）：$it",
                it.contains("DEFAULT"),
            )
            assertTrue(
                "DEFAULT 的值必须与实体的 defaultValue = \"''\" 逐字一致：$it",
                it.contains("DEFAULT ''"),
            )
        }
    }

    @Test
    fun `回填语句把课程级的教师与地点抄进课时`() {
        val sql = backfillSessionTeacherLocationSql()
        assertTrue(sql.contains("UPDATE course_sessions SET"))
        assertTrue(sql.contains("teacher = (SELECT c.teacher FROM courses c WHERE c.id = course_sessions.courseId)"))
        assertTrue(sql.contains("location = (SELECT c.location FROM courses c WHERE c.id = course_sessions.courseId)"))
    }

    @Test
    fun `回填语句在迁移序列里`() {
        assertTrue(
            "回填必须在两条 ALTER 之后执行",
            migration20To21Statements().last().contains("UPDATE course_sessions"),
        )
    }

    @Test
    fun `不删表`() {
        migration20To21Statements().forEach {
            assertTrue(
                "本次迁移不得删表：$it",
                !it.contains("DROP TABLE"),
            )
        }
    }

    @Test
    fun `不删数据`() {
        migration20To21Statements().forEach {
            assertTrue(
                "本次迁移不得删数据：$it",
                !it.contains("DELETE FROM"),
            )
        }
    }

    @Test
    fun `不动 courses 表结构`() {
        // 本次迁移只给 course_sessions 加列，courses 表一行都不碰
        migration20To21Statements().forEach {
            assertTrue(
                "本次迁移不得 ALTER courses 表：$it",
                !it.contains("ALTER TABLE `courses`"),
            )
        }
    }

    @Test
    fun `迁移版本号是 20 到 21`() {
        assertEquals(20, MIGRATION_20_21.startVersion)
        assertEquals(21, MIGRATION_20_21.endVersion)
    }

    @Test
    fun `App 数据库版本已经升到 21`() {
        assertEquals(21, APP_DATABASE_VERSION)
    }
}
