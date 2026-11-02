package com.example.personalassistant.core.backup.exporter

import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Phase 8 Sub-step 4：BackupExporter round-trip 单元测试
 *
 * 验证 export 行为：写入 ZIP 字节流 + 读取 ZIP 检查结构
 */
class BackupExporterTest {

    private fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `export 空 payload 输出有效 ZIP 结构`() {
        val exporter = BackupExporter(json())
        val outputStream = ByteArrayOutputStream()
        val result = exporter.export(BackupPayload(), outputStream)
        assertTrue("导出应该成功：$result", result is BackupResult.Success)

        val zipBytes = outputStream.toByteArray()
        assertTrue("ZIP 不为空", zipBytes.isNotEmpty())

        // 解压检查
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zipIn.readBytes()
                }
                entry = zipIn.nextEntry
            }
        }
        assertTrue("包含 manifest.json", entries.containsKey("manifest.json"))
        assertTrue("包含 checksums.json", entries.containsKey("checksums.json"))
        assertTrue("包含 data/semesters.json", entries.containsKey("data/semesters.json"))
        assertTrue("包含 data/tasks.json", entries.containsKey("data/tasks.json"))
    }

    @Test
    fun `export 15 张表 JSON 都写入`() {
        val exporter = BackupExporter(json())
        val outputStream = ByteArrayOutputStream()
        val result = exporter.export(BackupPayload(), outputStream)
        assertTrue(result is BackupResult.Success)

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(outputStream.toByteArray())).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zipIn.readBytes()
                }
                entry = zipIn.nextEntry
            }
        }

        // 验证 15 张表的 JSON 都存在（Phase 16：-sub_tasks +task_lists +event_categories +student_profile）
        val expectedTables = listOf(
            "user_profiles", "semesters", "courses", "tags", "task_lists", "tasks",
            "task_tag_cross_ref", "events", "event_categories", "categories",
            "monthly_budgets", "daily_pools", "transactions", "holidays",
            "student_profile",
        )
        expectedTables.forEach { table ->
            assertTrue("包含 data/$table.json", entries.containsKey("data/$table.json"))
            // 空表也是有效 JSON 数组
            assertTrue("$table 应为空数组 JSON", String(entries["data/$table.json"]!!).trim() == "[]")
        }
    }

    @Test
    fun `export IO 异常返回 Failure`() {
        val exporter = BackupExporter(json())
        // 模拟一个会立即抛错的 OutputStream
        val failingStream = object : java.io.OutputStream() {
            override fun write(b: Int) {
                throw java.io.IOException("test failure")
            }
        }

        val result = exporter.export(BackupPayload(), failingStream)
        when (result) {
            is BackupResult.Failure -> assertEquals(BackupError.EXPORT_FAILED, result.error)
            else -> error("应该失败")
        }
    }

}
