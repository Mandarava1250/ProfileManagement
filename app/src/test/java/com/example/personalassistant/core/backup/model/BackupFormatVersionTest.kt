package com.example.personalassistant.core.backup.model

import com.example.personalassistant.core.database.APP_DATABASE_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 Sub-step 4：BackupFormatVersion 单元测试
 */
class BackupFormatVersionTest {

    @Test
    fun `defaultZipName uses cyrene-backup prefix and zip suffix`() {
        val name = BackupFormatVersion.defaultZipName(0L)
        assertTrue("文件名应以 cyrene-backup- 开头：$name", name.startsWith("cyrene-backup-"))
        assertTrue("文件名应以 .zip 结尾：$name", name.endsWith(".zip"))
    }

    @Test
    fun `defaultZipName contains timestamp from input`() {
        // 不同时间戳应产生不同文件名
        val name1 = BackupFormatVersion.defaultZipName(1_700_000_000_000L)
        val name2 = BackupFormatVersion.defaultZipName(1_800_000_000_000L)
        assertNotEquals("不同时间戳应产生不同文件名", name1, name2)
    }

    @Test
    fun `APPLICATION 包名匹配预期`() {
        assertEquals("com.example.personalassistant", BackupFormatVersion.APPLICATION)
    }

    @Test
    fun `FORMAT_VERSION 应为 3`() {
        // Phase 16（v15）：备份格式升 v2 —— 表集合变了（新增 task_lists、
        // 废弃 sub_tasks / sub_task_tag_cross_ref），旧备份需要区分处理。
        // Phase 28 P2：**升到 v3** —— courses 表的 dayOfWeek/startTime/endTime
        // 三列**语义变了**（从「唯一的上课时段」收窄为「主时段镜像 = 第一个课时」），
        // 旧备份里这三列存的还是老语义，导入端必须能识别。
        // 未来如果升 v4，本测试提醒修改。
        assertEquals(3, BackupFormatVersion.FORMAT_VERSION)
    }

    @Test
    fun `DB_VERSION 应等于当前 schema version`() {
        // Phase 8 Sub-step 7：v6（holidays 表 + tasks.holidayStrategy）
        // Phase 8 Sub-step 9：v7（event_categories 表 + events.categoryId）
        // ⚠️ v8 当时没同步改这里（长期停在 7），是一处未被发现的老漂移
        // Phase 3：v9（user_profiles 表 + 记账 4 表 accountId）—— 同时修正老漂移
        // Phase 5：v10（transactions 转账两列 + user_profiles.initialBalance）
        //
        // ⚠️ Phase 5 修正：本测试以前一直写 `assertEquals(9, ...)`，比的是**字面量**。
        // 改 AppDatabase.version 注解不会让它红，所以上面那条注释吹的
        // 「每 +1 就会提醒」根本不成立 —— v9 那次漂移就是它漏掉的现场。
        // 现在改成引用 APP_DATABASE_VERSION（注解自己也用它）：注解是唯一真相源，
        // 只改其中一处时本测试才会真的失败。
        assertEquals(APP_DATABASE_VERSION, BackupFormatVersion.DB_VERSION)
    }

    @Test
    fun `默认值常量非空`() {
        assertTrue(BackupFormatVersion.FILE_MANIFEST.isNotEmpty())
        assertTrue(BackupFormatVersion.FILE_CHECKSUMS.isNotEmpty())
        assertTrue(BackupFormatVersion.DIR_DATA.isNotEmpty())
    }
}
