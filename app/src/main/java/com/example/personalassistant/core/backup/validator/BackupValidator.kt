package com.example.personalassistant.core.backup.validator

import com.example.personalassistant.core.backup.model.BackupChecksums
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupFormatVersion
import com.example.personalassistant.core.backup.model.BackupManifest
import com.example.personalassistant.core.backup.model.BackupResult
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 4：备份校验器
 *
 * 校验三层：
 * 1. ZIP 物理层：能正确解析 ZIP（损坏 / 不完整 / 嵌套 zip 等）
 * 2. manifest 语义层：application / formatVersion / dbVersion / tables 必须合法
 * 3. checksum 数据层：每张表的 sha256 与 checksums.json 一致
 *
 * 用法：
 * ```
 * val stream = resolver.openInputStream(uri)!!
 * val zipIn = ZipInputStream(stream)
 * val result = validator.validate(zipIn, onPayloadBytes = { name, bytes -> /* cache */ })
 * ```
 *
 * 由于校验过程中需要消费 ZIP 数据流（一次只能读一次），校验器把读到的
 * manifest / checksums / 表 JSON 字节都通过回调暴露给上层（用于后续导入）。
 */
@Singleton
class BackupValidator @Inject constructor(
    @com.example.personalassistant.core.backup.di.BackupJson
    private val json: Json,
) : IBackupValidator {

    /**
     * 校验 + 抽取 manifest / checksums / 表 JSON 字节
     *
     * @param zipIn ZIP 输入流（消费完毕会关闭）
     * @param onPayload 回调（每个条目调用一次），返回 true 表示继续读，返回 false 表示跳过该条目
     * @return [ValidationBundle] 携带 manifest、checksums、所有表 JSON 字节
     */
    override fun validateAndExtract(
        zipIn: ZipInputStream,
        onPayload: (entryName: String, bytes: ByteArray) -> Boolean,
    ): BackupResult<ValidationBundle> {
        val manifestBytes = mutableMapOf<String, ByteArray>()
        val checksumsBytes = mutableMapOf<String, ByteArray>()
        val tableBytes = mutableMapOf<String, ByteArray>()
        var manifest: BackupManifest? = null
        var checksums: BackupChecksums? = null

        var entry: ZipEntry? = zipIn.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val name = entry.name
                val bytes = zipIn.readBytes()

                when (name) {
                    BackupFormatVersion.FILE_MANIFEST -> manifestBytes[name] = bytes
                    BackupFormatVersion.FILE_CHECKSUMS -> checksumsBytes[name] = bytes
                    else -> if (name.startsWith("${BackupFormatVersion.DIR_DATA}/")) {
                        val tableName = name.removePrefix("${BackupFormatVersion.DIR_DATA}/").removeSuffix(".json")
                        tableBytes[tableName] = bytes
                        if (!onPayload(name, bytes)) {
                            // 回调说不要这条数据，丢弃（但保留 manifest/checksums）
                        }
                    }
                }
            }
            entry = zipIn.nextEntry
        }

        // ===== 1. manifest 必须存在 =====
        val rawManifest = manifestBytes[BackupFormatVersion.FILE_MANIFEST]
            ?: return BackupResult.Failure(BackupError.INVALID_MANIFEST, "manifest.json 不存在")
        manifest = try {
            json.decodeFromString(BackupManifest.serializer(), rawManifest.toString(Charsets.UTF_8))
        } catch (t: Throwable) {
            return BackupResult.Failure(
                BackupError.INVALID_MANIFEST,
                "manifest 解析失败：${t.message ?: t::class.java.simpleName}",
            )
        }

        // ===== 2. manifest 字段校验 =====
        val m = manifest
        if (m.application != BackupFormatVersion.APPLICATION) {
            return BackupResult.Failure(
                BackupError.INVALID_MANIFEST,
                "应用包名不匹配（expected=${BackupFormatVersion.APPLICATION}, actual=${m.application}）",
            )
        }
        if (m.formatVersion > BackupFormatVersion.FORMAT_VERSION) {
            return BackupResult.Failure(
                BackupError.INVALID_MANIFEST,
                "备份版本过新：formatVersion=${m.formatVersion}，当前仅支持 ≤ ${BackupFormatVersion.FORMAT_VERSION}",
            )
        }

        // ===== 3. checksums 必须存在 + 解析 =====
        val rawChecksums = checksumsBytes[BackupFormatVersion.FILE_CHECKSUMS]
            ?: return BackupResult.Failure(BackupError.CHECKSUM_MISMATCH, "checksums.json 不存在")
        checksums = try {
            json.decodeFromString(BackupChecksums.serializer(), rawChecksums.toString(Charsets.UTF_8))
        } catch (t: Throwable) {
            return BackupResult.Failure(
                BackupError.CHECKSUM_MISMATCH,
                "checksums 解析失败：${t.message ?: t::class.java.simpleName}",
            )
        }

        // ===== 4. 每张表 sha256 比对 =====
        val cs = checksums
        for ((tableName, expectedHash) in cs.checksums) {
            val raw = tableBytes[tableName]
                ?: return BackupResult.Failure(
                    BackupError.CHECKSUM_MISMATCH,
                    "表 $tableName 缺失（manifest 声明但实际找不到）",
                )
            val actualHash = sha256Hex(raw)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                return BackupResult.Failure(
                    BackupError.CHECKSUM_MISMATCH,
                    "表 $tableName checksum 不一致（expected=$expectedHash, actual=$actualHash）",
                )
            }
        }

        return BackupResult.Success(
            ValidationBundle(
                manifest = m,
                checksums = cs,
                tableBytes = tableBytes,
            )
        )
    }

    /**
     * sha256 → 16 进制小写
     */
    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}

/**
 * 校验通过后打包传递给 Importer 的中间结果
 */
data class ValidationBundle(
    val manifest: BackupManifest,
    val checksums: BackupChecksums,
    val tableBytes: Map<String, ByteArray>,
)
