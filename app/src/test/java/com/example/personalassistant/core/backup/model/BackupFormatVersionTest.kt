package com.example.personalassistant.core.backup.model

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
    fun `FORMAT_VERSION 应从 1 开始`() {
        // 未来如果升 v2，本测试提醒修改
        assertEquals(1, BackupFormatVersion.FORMAT_VERSION)
    }

    @Test
    fun `DB_VERSION 应等于当前 schema version 5`() {
        assertEquals(5, BackupFormatVersion.DB_VERSION)
    }

    @Test
    fun `默认值常量非空`() {
        assertTrue(BackupFormatVersion.FILE_MANIFEST.isNotEmpty())
        assertTrue(BackupFormatVersion.FILE_CHECKSUMS.isNotEmpty())
        assertTrue(BackupFormatVersion.DIR_DATA.isNotEmpty())
    }
}
