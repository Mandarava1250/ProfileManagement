package com.example.personalassistant.core.backup.model

/**
 * Phase 8 Sub-step 4：备份 / 恢复操作的密封结果
 *
 * 携带具体错误码（用于 UI 展示 + 日志）和上下文信息。
 * 不引入 throw / try-catch 让上层显式处理每一种失败模式。
 */
sealed class BackupResult<out T> {
    data class Success<T>(val value: T) : BackupResult<T>()

    /** 备份/恢复过程失败（IO、解析、数据库错误等）*/
    data class Failure(val error: BackupError, val message: String? = null) : BackupResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): BackupResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}

/**
 * 备份错误分类。
 *
 * - 备份侧（[BACKUP_*]）：写 ZIP / 序列化 / 写 SAF 时失败
 * - 恢复侧（[RESTORE_*]）：读 ZIP / 解析 / 写 Room 时失败
 * - 设置侧（[PREF_*]）：DataStore 读写失败
 */
enum class BackupError {
    // ===== 通用 =====
    /** IO 错误（SAF 读写 / 文件不存在 / 权限不足）*/
    IO_ERROR,

    // ===== 备份（导出）=====
    /** 序列化失败（kotlinx-serialization 解析 / 编码异常）*/
    SERIALIZATION_FAILED,
    /** 写入 SAF 失败（用户取消 / 路径不可写）*/
    EXPORT_FAILED,

    // ===== 恢复（导入）=====
    /** ZIP 格式损坏 / 解析失败 */
    INVALID_ZIP,
    /** manifest 校验失败（格式版本不匹配 / 应用包名错 / 字段缺失）*/
    INVALID_MANIFEST,
    /** checksum 校验失败（表 JSON 被篡改）*/
    CHECKSUM_MISMATCH,
    /** 数据库写入失败（事务回滚）*/
    IMPORT_FAILED,

    // ===== 偏好 =====
    /** DataStore 读写失败 */
    PREF_READ_FAILED,
    PREF_WRITE_FAILED,
}
