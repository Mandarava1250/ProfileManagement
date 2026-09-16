package com.example.personalassistant.feature.settings.backup

import com.example.personalassistant.core.backup.model.BackupChecksums
import com.example.personalassistant.core.backup.model.BackupError
import com.example.personalassistant.core.backup.model.BackupManifest
import com.example.personalassistant.core.backup.model.BackupResult
import com.example.personalassistant.core.backup.validator.IBackupValidator
import com.example.personalassistant.core.backup.validator.ValidationBundle
import java.util.zip.ZipInputStream

/**
 * Phase 10 10a-1：备份校验器的 Fake
 *
 * 实现 [IBackupValidator] 接口（Phase 10 抽出），直接返回测试预设的 [BackupResult]，
 * 跳过真 ZIP 解析 + sha256 比对。
 *
 * ## 用法
 * - [setSuccess]：下次调 `validateAndExtract` 返回 `Success(ValidationBundle(...))`，
 *   可选传 `tableBytes`（Fake 各表 JSON 字节，VM 会再用自己持有的 Json 反序列化）
 * - [setFailure]：返回 `Failure(INVALID_ZIP, ...)`，让 VM 走 `PreviewUiState.Error` 分支
 *
 * ## 设计取舍
 * - Phase 10 之前是继承 `open class BackupValidator` + 覆写 `validateAndExtract`。
 *   但 `validateAndExtract` 默认 final 无法 override，所以 Phase 10 改成接口化：
 *   生产代码抽 `IBackupValidator` 接口 → Fake 直接 `:` 实现，不再需要继承类
 * - 不持有任何 IO/DB 依赖，是纯函数式 fake
 * - 不保留 manifest/checksums 真实校验语义 —— 测试目标是 VM 的状态机，不是 validator 本身
 */
class FakeBackupValidator : IBackupValidator {

    private var nextResult: BackupResult<ValidationBundle> = BackupResult.Success(defaultBundle())

    /** validateAndExtract 被调用的次数 */
    var validateAndExtractCallCount: Int = 0
        private set

    /** 最近一次实际返回的 result，便于断言 */
    var lastReturnedResult: BackupResult<ValidationBundle>? = null
        private set

    override fun validateAndExtract(
        zipIn: ZipInputStream,
        onPayload: (entryName: String, bytes: ByteArray) -> Boolean,
    ): BackupResult<ValidationBundle> {
        validateAndExtractCallCount++
        // 不消费 zipIn（产线实现会迭代 entries 读出 manifest/checksums/表 JSON）。
        // 本 fake 不关心流内容——直接返回预设结果。
        return nextResult.also { lastReturnedResult = it }
    }

    /**
     * 让下次 `validateAndExtract` 返回 Success。
     *
     * @param payloadBytes 各表 JSON 字节。生产代码路径下，VM 拿到 ValidationBundle 后会逐表
     *        调 `json.decodeFromString(...)` 解码。测试可传空 map 让所有表解码为空列表。
     */
    fun setSuccess(payloadBytes: Map<String, ByteArray> = emptyMap()) {
        nextResult = BackupResult.Success(
            ValidationBundle(
                manifest = defaultManifest(),
                checksums = BackupChecksums(emptyMap()),
                tableBytes = payloadBytes,
            ),
        )
    }

    /**
     * 让下次 `validateAndExtract` 返回 Failure。
     */
    fun setFailure(
        error: BackupError = BackupError.INVALID_ZIP,
        message: String? = "fake validator failure",
    ) {
        nextResult = BackupResult.Failure(error, message)
    }

    private fun defaultManifest(): BackupManifest = BackupManifest(
        application = "com.example.personalassistant",
        formatVersion = 1,
        dbVersion = 0,
        createdAt = 0L,
        tables = BackupManifest.DEFAULT_TABLES,
    )

    private fun defaultBundle(): ValidationBundle = ValidationBundle(
        manifest = defaultManifest(),
        checksums = BackupChecksums(emptyMap()),
        tableBytes = emptyMap(),
    )
}