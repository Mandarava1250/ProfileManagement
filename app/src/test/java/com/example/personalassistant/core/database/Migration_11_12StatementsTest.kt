package com.example.personalassistant.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration_11_12 SQL 断言（JVM 层，无需设备）
 *
 * 这轮迁移最敏感的点是：
 * 1. **绝不能有 `ALTER TABLE ... ADD COLUMN`** —— v11 的 `student_profile` 里
 *    `gpaRuleJson` **早就存在**（11.json 可见），再 ADD 一次必然撞
 *    `duplicate column name: gpaRuleJson`（这正是本轮修的 bug）
 * 2. v12 的形状要求 `gpaTarget` / `resumeText` 带 DEFAULT、`gpaRuleJson` 变 nullable ——
 *    SQLite 改不了已有列的 NOT NULL / DEFAULT，只能**重建表**
 * 3. 重建表的语句顺序有依赖：清临时表 → 建新表 → 搬数据 → （搬 gpaRuleJson）→ 删旧表 → 改名
 * 4. 源表有两种 v11 形状（有 / 没有 gpaRuleJson），两种都要能收敛到同一张 v12 表
 */
class Migration_11_12StatementsTest {

    /** v11 由 Room 自己建的库（11.json 形状）：源表**有** gpaRuleJson */
    private val withRule = migration11To12Statements(sourceHasGpaRuleJsonColumn = true)

    /** v10→v11 升级上来的库（[Migration_10_11] 手写建表）：源表**没有** gpaRuleJson */
    private val withoutRule = migration11To12Statements(sourceHasGpaRuleJsonColumn = false)

    @Test
    fun `不能有盲目的 ADD COLUMN —— duplicate column 的来源`() {
        assertFalse("v11 的表里已有 gpaRuleJson，不能再 ADD：$withRule", withRule.any { it.contains("ADD COLUMN") })
        assertFalse(
            "v11 的表里已有 gpaRuleJson，不能再 ADD：$withoutRule",
            withoutRule.any { it.contains("ADD COLUMN") },
        )
    }

    @Test
    fun `新表形状必须与 12_json 对齐`() {
        val create = withRule.single { it.contains("CREATE TABLE IF NOT EXISTS") }
        assertTrue("gpaTarget 必须 NOT NULL DEFAULT 4.0：$create", create.contains("`gpaTarget` REAL NOT NULL DEFAULT 4.0"))
        assertTrue("resumeText 必须 NOT NULL DEFAULT ''：$create", create.contains("`resumeText` TEXT NOT NULL DEFAULT ''"))
        assertTrue("updatedAt 必须 NOT NULL：$create", create.contains("`updatedAt` INTEGER NOT NULL"))
        assertTrue("accountId 必为主键：$create", create.contains("PRIMARY KEY(`accountId`)"))

        // gpaRuleJson 在 v12 里是 **nullable** + DEFAULT ''（v11 的 NOT NULL 形状必须被改掉）
        val ruleLine = create.lines().single { it.contains("`gpaRuleJson`") }
        assertFalse("gpaRuleJson 不能再是 NOT NULL：$ruleLine", ruleLine.contains("NOT NULL"))
        assertTrue("gpaRuleJson 必须带 DEFAULT ''：$ruleLine", ruleLine.contains("DEFAULT ''"))
    }

    @Test
    fun `源表有 gpaRuleJson 时才多一条搬数据的 UPDATE`() {
        assertEquals("源表有 gpaRuleJson 时是 6 条语句", 6, withRule.size)
        assertEquals("源表没有 gpaRuleJson 时是 5 条语句", 5, withoutRule.size)
        assertTrue("有 gpaRuleJson 时必须搬过去", withRule.any { it.startsWith("UPDATE") })
        assertFalse("没有 gpaRuleJson 时不该有搬列的 UPDATE", withoutRule.any { it.startsWith("UPDATE") })
    }

    @Test
    fun `语句顺序是 清临时表 → 建新表 → 搬数据 → 删旧表 → 改名`() {
        val verbs = withRule.map { it.substringBefore(' ') }
        assertEquals(
            "语句顺序不能变：建表 → 搬数据 → 删旧表 → 改名",
            listOf("DROP", "CREATE", "INSERT", "UPDATE", "DROP", "ALTER"),
            verbs,
        )
        // 第一条必须先清干净临时表，否则迁移重跑会撞「表已存在」
        assertTrue("必须先清临时表：${withRule.first()}", withRule.first().contains("DROP TABLE IF EXISTS"))
        // 删旧表必须在搬数据之后、改名之前
        val copyIndex = withRule.indexOfFirst { it.startsWith("INSERT") }
        // 宽松匹配：不要把整句 SQL 硬编码进断言 —— 引号转义差一个字符就会误报，
        // 而这里真正要锁的是「先搬数据、再删旧表、最后改名」这个顺序
        val dropIndex = withRule.indexOfFirst { it.startsWith("DROP TABLE") && !it.contains("IF EXISTS") }
        val renameIndex = withRule.indexOfFirst { it.startsWith("ALTER TABLE") }
        assertTrue("搬数据必须在删旧表之前", copyIndex < dropIndex)
        assertTrue("删旧表必须在改名之前", dropIndex < renameIndex)
        assertTrue(
            "最后一步必须把临时表改名成正式表名：${withRule.last()}",
            withRule.last().startsWith("ALTER TABLE") &&
                withRule.last().contains("RENAME TO") &&
                withRule.last().contains(STUDENT_PROFILE_TABLE),
        )
    }

    @Test
    fun `搬数据要带上 gpaTarget 与 resumeText 的兜底默认值`() {
        val copy = withRule.single { it.startsWith("INSERT") }
        assertTrue("gpaTarget 要兜底 4.0：$copy", copy.contains("COALESCE(`gpaTarget`, 4.0)"))
        assertTrue("resumeText 要兜底 ''：$copy", copy.contains("COALESCE(`resumeText`, '')"))
        assertTrue("主键 accountId 必须搬：$copy", copy.contains("`accountId`"))
        assertTrue("updatedAt 必须搬：$copy", copy.contains("`updatedAt`"))
    }
}
