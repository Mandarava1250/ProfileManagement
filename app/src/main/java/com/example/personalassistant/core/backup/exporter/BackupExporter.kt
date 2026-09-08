package com.example.personalassistant.core.backup.exporter

import com.example.personalassistant.core.backup.di.BackupJson
import com.example.personalassistant.core.backup.model.BackupChecksums
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupFormatVersion
import com.example.personalassistant.core.backup.model.BackupManifest
import com.example.personalassistant.core.backup.model.BackupPayload
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.holiday.model.HolidayEntity
import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.data.local.TaskTagCrossRef
import com.example.personalassistant.feature.timetable.data.local.CourseEntity
import com.example.personalassistant.feature.timetable.data.local.SemesterEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 4：备份导出器（Payload -> ZIP）
 *
 * 把 [BackupPayload] 序列化为 JSON 写入 ZIP 压缩包，并写入 manifest / checksums。
 */
@Singleton
class BackupExporter @Inject constructor(
    @BackupJson private val json: Json,
) {

    /**
     * 把 payload 写入 [outputStream]，自动包裹成 ZIP
     */
    fun export(payload: BackupPayload, outputStream: OutputStream): BackupResult<Unit> {
        return try {
            ZipOutputStream(outputStream).use { zipOut ->
                val checksums = mutableMapOf<String, String>()

                writeSemesters(zipOut, payload.semesters, checksums)
                writeCourses(zipOut, payload.courses, checksums)
                writeTags(zipOut, payload.tags, checksums)
                writeTasks(zipOut, payload.tasks, checksums)
                writeSubTasks(zipOut, payload.subTasks, checksums)
                writeTaskTagCrossRefs(zipOut, payload.taskTagCrossRefs, checksums)
                writeEvents(zipOut, payload.events, checksums)
                writeCategories(zipOut, payload.categories, checksums)
                writeMonthlyBudgets(zipOut, payload.monthlyBudgets, checksums)
                writeDailyPools(zipOut, payload.dailyPools, checksums)
                writeTransactions(zipOut, payload.transactions, checksums)
                writeHolidays(zipOut, payload.holidays, checksums)

                val manifest = BackupManifest(
                    application = BackupFormatVersion.APPLICATION,
                    formatVersion = BackupFormatVersion.FORMAT_VERSION,
                    dbVersion = BackupFormatVersion.DB_VERSION,
                    createdAt = System.currentTimeMillis(),
                    tables = BackupManifest.DEFAULT_TABLES,
                )
                val manifestJson = json.encodeToString(BackupManifest.serializer(), manifest)
                zipOut.putNextEntry(ZipEntry(BackupFormatVersion.FILE_MANIFEST))
                zipOut.write(manifestJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                val checksumsWrapper = BackupChecksums(checksums)
                val checksumsJson = json.encodeToString(BackupChecksums.serializer(), checksumsWrapper)
                zipOut.putNextEntry(ZipEntry(BackupFormatVersion.FILE_CHECKSUMS))
                zipOut.write(checksumsJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
            BackupResult.Success(Unit)
        } catch (t: Throwable) {
            BackupResult.Failure(
                error = BackupError.EXPORT_FAILED,
                message = t.message ?: "export failed: ${t::class.java.simpleName}",
            )
        }
    }

    // 12 张表分别 dispatch（用唯一方法名避免 JVM signature clash）
    private fun writeSemesters(zipOut: ZipOutputStream, data: List<SemesterEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "semesters",
            json.encodeToString(ListSerializer(SemesterEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeCourses(zipOut: ZipOutputStream, data: List<CourseEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "courses",
            json.encodeToString(ListSerializer(CourseEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeTags(zipOut: ZipOutputStream, data: List<TagEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "tags",
            json.encodeToString(ListSerializer(TagEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeTasks(zipOut: ZipOutputStream, data: List<TaskEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "tasks",
            json.encodeToString(ListSerializer(TaskEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeSubTasks(zipOut: ZipOutputStream, data: List<SubTaskEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "sub_tasks",
            json.encodeToString(ListSerializer(SubTaskEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeTaskTagCrossRefs(zipOut: ZipOutputStream, data: List<TaskTagCrossRef>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "task_tag_cross_ref",
            json.encodeToString(ListSerializer(TaskTagCrossRef.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeEvents(zipOut: ZipOutputStream, data: List<EventEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "events",
            json.encodeToString(ListSerializer(EventEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeCategories(zipOut: ZipOutputStream, data: List<CategoryEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "categories",
            json.encodeToString(ListSerializer(CategoryEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeMonthlyBudgets(zipOut: ZipOutputStream, data: List<MonthlyBudgetEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "monthly_budgets",
            json.encodeToString(ListSerializer(MonthlyBudgetEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeDailyPools(zipOut: ZipOutputStream, data: List<DailyPoolEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "daily_pools",
            json.encodeToString(ListSerializer(DailyPoolEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeTransactions(zipOut: ZipOutputStream, data: List<TransactionEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "transactions",
            json.encodeToString(ListSerializer(TransactionEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeHolidays(zipOut: ZipOutputStream, data: List<HolidayEntity>, checksums: MutableMap<String, String>) =
        writeBytes(zipOut, "holidays",
            json.encodeToString(ListSerializer(HolidayEntity.serializer()), data).toByteArray(Charsets.UTF_8),
            checksums)

    private fun writeBytes(
        zipOut: ZipOutputStream,
        tableName: String,
        jsonBytes: ByteArray,
        checksums: MutableMap<String, String>,
    ) {
        zipOut.putNextEntry(ZipEntry("${BackupFormatVersion.DIR_DATA}/$tableName.json"))
        zipOut.write(jsonBytes)
        zipOut.closeEntry()
        checksums[tableName] = sha256Hex(jsonBytes)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
