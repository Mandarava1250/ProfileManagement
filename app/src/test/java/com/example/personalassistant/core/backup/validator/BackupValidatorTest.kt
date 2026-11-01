package com.example.personalassistant.core.backup.validator

import com.example.personalassistant.core.backup.model.BackupChecksums
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupFormatVersion
import com.example.personalassistant.core.backup.model.BackupManifest
import com.example.personalassistant.core.backup.model.BackupResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Phase 8 Sub-step 4：BackupValidator unit tests
 *
 * Tests three validation layers: ZIP physical / manifest semantic / checksum data
 */
class BackupValidatorTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun buildValidZip(
        manifest: BackupManifest,
        checksums: BackupChecksums,
        tableBytes: Map<String, String>,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zipOut ->
            zipOut.putNextEntry(ZipEntry(BackupFormatVersion.FILE_MANIFEST))
            zipOut.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry(BackupFormatVersion.FILE_CHECKSUMS))
            zipOut.write(json.encodeToString(BackupChecksums.serializer(), checksums).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            tableBytes.forEach { (name, content) ->
                zipOut.putNextEntry(ZipEntry(BackupFormatVersion.DIR_DATA + "/" + name + ".json"))
                zipOut.write(content.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private val sampleManifest = BackupManifest(
        application = BackupFormatVersion.APPLICATION,
        formatVersion = BackupFormatVersion.FORMAT_VERSION,
        dbVersion = BackupFormatVersion.DB_VERSION,
        createdAt = 1_700_000_000_000L,
        tables = BackupManifest.DEFAULT_TABLES,
    )

    @Test
    fun `valid ZIP returns ValidationBundle`() {
        val validator = BackupValidator(json)
        val checksums = BackupChecksums(mapOf("tasks" to sha256("[]")))
        val zip = buildValidZip(sampleManifest, checksums, mapOf("tasks" to "[]"))
        val zipIn = ZipInputStream(ByteArrayInputStream(zip))

        val result = validator.validateAndExtract(zipIn)
        when (result) {
            is BackupResult.Success<*> -> {
                val bundle = (result as BackupResult.Success<ValidationBundle>).value
                assertEquals(sampleManifest.application, bundle.manifest.application)
                assertEquals(1, bundle.checksums.checksums.size)
                assertEquals("[]", String(bundle.tableBytes["tasks"]!!))
            }
            is BackupResult.Failure -> error("should not fail: " + result.message)
        }
    }

    @Test
    fun `checksum mismatch returns CHECKSUM_MISMATCH`() {
        val validator = BackupValidator(json)
        val wrongChecksums = BackupChecksums(mapOf("tasks" to sha256("WRONG_DATA")))
        val zip = buildValidZip(sampleManifest, wrongChecksums, mapOf("tasks" to "[]"))
        val zipIn = ZipInputStream(ByteArrayInputStream(zip))

        val result = validator.validateAndExtract(zipIn)
        when (result) {
            is BackupResult.Failure -> assertEquals(BackupError.CHECKSUM_MISMATCH, result.error)
            is BackupResult.Success<*> -> error("should fail")
        }
    }

    @Test
    fun `wrong application returns INVALID_MANIFEST`() {
        val validator = BackupValidator(json)
        val wrongManifest = sampleManifest.copy(application = "com.fake.app")
        val checksums = BackupChecksums(emptyMap())
        val zip = buildValidZip(wrongManifest, checksums, emptyMap())
        val zipIn = ZipInputStream(ByteArrayInputStream(zip))

        val result = validator.validateAndExtract(zipIn)
        when (result) {
            is BackupResult.Failure -> assertEquals(BackupError.INVALID_MANIFEST, result.error)
            is BackupResult.Success<*> -> error("should fail")
        }
    }

    @Test
    fun `future formatVersion returns INVALID_MANIFEST`() {
        val validator = BackupValidator(json)
        val futureManifest = sampleManifest.copy(formatVersion = 999)
        val checksums = BackupChecksums(emptyMap())
        val zip = buildValidZip(futureManifest, checksums, emptyMap())
        val zipIn = ZipInputStream(ByteArrayInputStream(zip))

        val result = validator.validateAndExtract(zipIn)
        when (result) {
            is BackupResult.Failure -> {
                assertEquals(BackupError.INVALID_MANIFEST, result.error)
                assertTrue(result.message!!.contains("formatVersion"))
            }
            is BackupResult.Success<*> -> error("should fail")
        }
    }

    @Test
    fun `missing manifest returns INVALID_MANIFEST`() {
        val validator = BackupValidator(json)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zipOut ->
            zipOut.putNextEntry(ZipEntry(BackupFormatVersion.FILE_CHECKSUMS))
            zipOut.write("{}".toByteArray())
            zipOut.closeEntry()
        }
        val zipIn = ZipInputStream(ByteArrayInputStream(baos.toByteArray()))

        val result = validator.validateAndExtract(zipIn)
        when (result) {
            is BackupResult.Failure -> assertEquals(BackupError.INVALID_MANIFEST, result.error)
            is BackupResult.Success<*> -> error("should fail")
        }
    }

    @Test
    fun `declared table missing returns CHECKSUM_MISMATCH`() {
        val validator = BackupValidator(json)
        val checksums = BackupChecksums(mapOf("tasks" to sha256("[]")))
        val zip = buildValidZip(sampleManifest, checksums, emptyMap()) // no tasks.json
        val zipIn = ZipInputStream(ByteArrayInputStream(zip))

        val result = validator.validateAndExtract(zipIn)
        when (result) {
            is BackupResult.Failure -> assertEquals(BackupError.CHECKSUM_MISMATCH, result.error)
            is BackupResult.Success<*> -> error("should fail")
        }
    }

    private fun sha256(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}

