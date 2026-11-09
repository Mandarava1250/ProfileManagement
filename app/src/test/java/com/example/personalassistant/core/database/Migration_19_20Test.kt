package com.example.personalassistant.core.database

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 28 P1：Migration 19 → 20 的 DDL 断言
 *
 * 为什么要测这个：Room 在**运行期**才校验「迁移出来的表」与「Entity 声明的表」是否一致，
 * 编译期看不出来 —— DDL 写错一个字符就是线上用户一升级就崩。
 * 所以把关键片段断言在 JVM 单测里。
 */
class Migration_19_20Test {

    @Test
    fun `creates course_sessions table`() {
        val sql = createCourseSessionsTableSql()
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `course_sessions`"))
        assertTrue(sql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
        assertTrue(sql.contains("`courseId` INTEGER NOT NULL"))
        assertTrue(sql.contains("`sortOrder` INTEGER NOT NULL"))
        assertTrue(sql.contains("`dayOfWeek` INTEGER NOT NULL"))
        assertTrue(sql.contains("`startTime` INTEGER NOT NULL"))
        assertTrue(sql.contains("`endTime` INTEGER NOT NULL"))
        // 外键：课时挂在课程下，删课连带删课时
        assertTrue(sql.contains("FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`)"))
        assertTrue(sql.contains("ON UPDATE NO ACTION ON DELETE CASCADE"))
    }

    @Test
    fun `create table statement is idempotent`() {
        // 建表必须带 IF NOT EXISTS —— 迁移重跑不能炸
        assertTrue(createCourseSessionsTableSql().contains("IF NOT EXISTS"))
    }

    @Test
    fun `creates index on courseId`() {
        val statements = migration19To20Statements()
        val indexSql = statements.first { it.contains("index_course_sessions_courseId`") }
        assertTrue(indexSql.contains("CREATE INDEX IF NOT EXISTS"))
        assertTrue(indexSql.contains("`course_sessions` (`courseId`)"))
    }

    @Test
    fun `creates unique index on courseId and sortOrder`() {
        val statements = migration19To20Statements()
        val indexSql = statements.first { it.contains("index_course_sessions_courseId_sortOrder") }
        assertTrue(indexSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS"))
        assertTrue(indexSql.contains("`course_sessions` (`courseId`, `sortOrder`)"))
    }

    @Test
    fun `all create statements are idempotent`() {
        migration19To20Statements().filter { it.contains("CREATE") }.forEach {
            assertTrue("语句必须带 IF NOT EXISTS：$it", it.contains("IF NOT EXISTS"))
        }
    }

    @Test
    fun `seeds existing courses into course_sessions`() {
        val sql = seedCourseSessionsFromCoursesSql()
        // 存量课程迁移后各自变成「有一个课时的课」，行为与改造前一致
        assertTrue(sql.contains("INSERT INTO course_sessions"))
        assertTrue(sql.contains("SELECT id, 0, dayOfWeek, startTime, endTime FROM courses"))
    }

    @Test
    fun `does not drop any table`() {
        migration19To20Statements().forEach {
            assertTrue(
                "本次迁移不得删表：$it",
                !it.contains("DROP TABLE"),
            )
        }
    }

    @Test
    fun `does not delete any course`() {
        // courses 表一行都不能少 —— 这次迁移只加表 + 播种
        migration19To20Statements().forEach {
            assertTrue(
                "本次迁移不得删课程：$it",
                !it.contains("DELETE FROM courses"),
            )
        }
    }

    @Test
    fun `does not alter existing tables`() {
        // 本次迁移只允许「新建表 + 播种」，不动任何已有表结构
        migration19To20Statements().forEach {
            assertTrue(
                "本次迁移不得 ALTER 已有表：$it",
                !it.contains("ALTER TABLE"),
            )
        }
    }
}
