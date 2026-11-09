package com.example.personalassistant.core.backup.exporter

import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupManifest
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.feature.timetable.data.local.CourseSessionEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `export 20 张表 JSON 都写入`() {
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

        // 验证 20 张表的 JSON 都存在
        // （Phase 16：-sub_tasks +task_lists +event_categories +student_profile；
        //   Phase 23：+habits +habit_checkins；
        //   批次 C：+coin_pools；
        //   批次 D：+coin_pool_settlements；
        //   Phase 28 P2：+course_sessions）
        val expectedTables = listOf(
            "user_profiles", "semesters", "courses", "course_sessions", "tags",
            "task_lists", "tasks",
            "task_tag_cross_ref", "events", "event_categories", "categories",
            "monthly_budgets", "daily_pools", "transactions", "holidays",
            "student_profile", "habits", "habit_checkins", "coin_pools",
            "coin_pool_settlements",
        )
        expectedTables.forEach { table ->
            assertTrue("包含 data/$table.json", entries.containsKey("data/$table.json"))
            // 空表也是有效 JSON 数组
            assertTrue("$table 应为空数组 JSON", String(entries["data/$table.json"]!!).trim() == "[]")
        }
        // 表清单与实际写出的 ZIP 条目一一对应（漏写一张表 = 用户备份静默丢数据）
        assertEquals(
            "ZIP 里的 data/*.json 数量应与 DEFAULT_TABLES 一致",
            BackupManifest.DEFAULT_TABLES.size,
            entries.keys.count { it.startsWith("data/") },
        )
    }

    /**
     * Phase 28 P2：课时表必须**进出都在**备份里
     *
     * 漏了课时表 = 导出的备份恢复后课程变成「没有课时」，
     * 用户的一门课三个时段会退化成一个。
     */
    @Test
    fun `export 写出的 course_sessions 内容与输入一致`() {
        val exporter = BackupExporter(json())
        val session = CourseSessionEntity(
            id = 7L,
            courseId = 1L,
            sortOrder = 1,
            dayOfWeek = 3,
            startTime = 600,
            endTime = 690,
        )
        val outputStream = ByteArrayOutputStream()
        val result = exporter.export(
            BackupPayload(courseSessions = listOf(session)),
            outputStream,
        )
        assertTrue(result is BackupResult.Success)

        var jsonBytes: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(outputStream.toByteArray())).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == "data/course_sessions.json") {
                    jsonBytes = zipIn.readBytes()
                }
                entry = zipIn.nextEntry
            }
        }
        assertNotNull("必须写出 data/course_sessions.json", jsonBytes)

        val decoded = json().decodeFromString(
            ListSerializer(CourseSessionEntity.serializer()),
            jsonBytes!!.toString(Charsets.UTF_8),
        )
        assertEquals(1, decoded.size)
        assertEquals(session, decoded[0])
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
