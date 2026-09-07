package com.example.personalassistant.core.backup.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 8 Sub-step 4：BackupManifest + BackupPayload + BackupResult unit tests
 */
class BackupSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `BackupManifest serialize round-trip`() {
        val original = BackupManifest(
            application = BackupFormatVersion.APPLICATION,
            formatVersion = BackupFormatVersion.FORMAT_VERSION,
            dbVersion = BackupFormatVersion.DB_VERSION,
            createdAt = 1_700_000_000_000L,
            tables = BackupManifest.DEFAULT_TABLES,
        )
        val encoded = json.encodeToString(BackupManifest.serializer(), original)
        val decoded = json.decodeFromString(BackupManifest.serializer(), encoded)

        assertEquals(original.application, decoded.application)
        assertEquals(original.formatVersion, decoded.formatVersion)
        assertEquals(original.dbVersion, decoded.dbVersion)
        assertEquals(original.createdAt, decoded.createdAt)
        assertEquals(original.tables, decoded.tables)
    }

    @Test
    fun `BackupPayload empty serialize round-trip`() {
        val empty = BackupPayload()
        val encoded = json.encodeToString(BackupPayload.serializer(), empty)
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)
        assertEquals(0, decoded.totalRecordCount)
    }

    @Test
    fun `BackupPayload totalRecordCount is sum of all lists`() {
        val payload = BackupPayload(
            semesters = listOf(
                com.example.personalassistant.feature.timetable.data.local.SemesterEntity(
                    id = 1, name = "2024-1", startDate = 0L, endDate = 1L,
                ),
            ),
            transactions = listOf(
                com.example.personalassistant.feature.finance.data.local.TransactionEntity(
                    id = 1, amount = 10.0, type = 0, categoryId = 1, date = 0L,
                ),
                com.example.personalassistant.feature.finance.data.local.TransactionEntity(
                    id = 2, amount = 20.0, type = 0, categoryId = 1, date = 0L,
                ),
            ),
        )
        assertEquals(3, payload.totalRecordCount)
    }

    @Test
    fun `BackupResult Success carries value`() {
        val result: BackupResult<Int> = BackupResult.Success(42)
        when (result) {
            is BackupResult.Success<*> -> {
                val success = result as BackupResult.Success<Int>
                assertEquals(42, success.value)
            }
            is BackupResult.Failure -> error("should be Success")
        }
    }

    @Test
    fun `BackupResult Failure carries error and message`() {
        val result = BackupResult.Failure(BackupError.IO_ERROR, "test message")
        when (result) {
            is BackupResult.Failure -> {
                assertEquals(BackupError.IO_ERROR, result.error)
                assertEquals("test message", result.message)
            }
            is BackupResult.Success<*> -> error("should be Failure")
        }
    }

    @Test
    fun `BackupResult map transforms Success`() {
        val result: BackupResult<Int> = BackupResult.Success(10)
        val mapped = result.map { it * 2 }
        assertEquals(BackupResult.Success(20), mapped)
    }

    @Test
    fun `BackupResult map passes Failure through`() {
        val result: BackupResult<Int> = BackupResult.Failure(BackupError.INVALID_ZIP, "err")
        val mapped = result.map { it * 2 }
        assertEquals(result, mapped)
    }

    @Test
    fun `BackupError enum has all expected entries`() {
        val errors = BackupError.values().toSet()
        // 关键错误码都存在
        assertEquals(true, BackupError.IO_ERROR in errors)
        assertEquals(true, BackupError.SERIALIZATION_FAILED in errors)
        assertEquals(true, BackupError.EXPORT_FAILED in errors)
        assertEquals(true, BackupError.INVALID_ZIP in errors)
        assertEquals(true, BackupError.INVALID_MANIFEST in errors)
        assertEquals(true, BackupError.CHECKSUM_MISMATCH in errors)
        assertEquals(true, BackupError.IMPORT_FAILED in errors)
        assertEquals(true, BackupError.PREF_READ_FAILED in errors)
        assertEquals(true, BackupError.PREF_WRITE_FAILED in errors)
    }
}

