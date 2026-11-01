package com.example.personalassistant.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12：Migration_10_11 SQL 断言
 *
 * 这轮迁移最敏感的点是：
 * 1. courses.credit / gradePoint 必须是 nullable（NULL = 未填，混入"算 0 学分"会把 GPA 拉低）
 * 2. 不能写 `DEFAULT 0` —— Entity 是 `Double?` 没默认值，写了会与 Room schema 对不上
 * 3. student_profile 主键是 accountId
 * 4. student_profile 不能有 FK 引到 user_profiles（避免循环依赖 + 简单）
 */
class Migration_10_11StatementsTest {

    private val statements = MIGRATION_10_11_STATEMENTS

    @Test
    fun `courses 加 credit 且 nullable`() {
        val alter = statements.single {
            it.contains("ALTER TABLE `courses`") && it.contains("ADD COLUMN `credit`")
        }
        assertTrue("credit 必须可空：$alter", alter.contains("DEFAULT NULL"))
        assertFalse("credit 不能 NOT NULL：$alter", alter.contains("NOT NULL"))
    }

    @Test
    fun `courses 加 gradePoint 且 nullable`() {
        val alter = statements.single {
            it.contains("ALTER TABLE `courses`") && it.contains("ADD COLUMN `gradePoint`")
        }
        assertTrue("gradePoint 必须可空：$alter", alter.contains("DEFAULT NULL"))
        assertFalse("gradePoint 不能 NOT NULL：$alter", alter.contains("NOT NULL"))
    }

    @Test
    fun `student_profile 表带 accountId 主键`() {
        val create = statements.single { it.contains("CREATE TABLE IF NOT EXISTS `student_profile`") }
        assertTrue("accountId 必为主键：$create", create.contains("PRIMARY KEY(`accountId`)"))
        assertTrue("必含 gpaTarget 列：$create", create.contains("`gpaTarget`"))
        assertTrue("必含 resumeText 列：$create", create.contains("`resumeText`"))
        assertTrue("必含 updatedAt 列：$create", create.contains("`updatedAt`"))
    }

    @Test
    fun `student_profile 不能带外键（避免循环依赖）`() {
        val create = statements.single { it.contains("CREATE TABLE IF NOT EXISTS `student_profile`") }
        assertFalse("student_profile 不应声明外键：$create", create.contains("FOREIGN KEY"))
        assertFalse("student_profile 不应引用 user_profiles：$create", create.contains("user_profiles"))
    }

    @Test
    fun `迁移语句数与预期一致`() {
        // 2 条 ALTER + 1 条 CREATE = 3
        assertEquals(3, statements.size)
    }
}
