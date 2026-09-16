package com.example.personalassistant.core.backup.validator

import java.util.zip.ZipInputStream

/**
 * Phase 10 10a-1（重构为接口）：备份校验器最小契约接口
 *
 * 把 [BackupValidator] 从具体类抽出接口，让 VM 通过接口注入，方便 JVM 单测写 fake。
 *
 * ## 为什么是这个时点抽
 * Plan 10a 之前 [BackupValidator] 是 `open class`，但 `validateAndExtract` 是默认 final
 * 方法无法 override。两条路：(A) 给方法加 `open`，(B) 抽接口。**Phase 10 采用 (B)**，
 * 与 [com.example.personalassistant.core.backup.snapshot.BackupSnapshotProvider] 一致
 * （Phase 9 A2 已抽出）。
 *
 * ## 与原 open class 的关系
 * - [BackupValidator] 仍是 `@Singleton`，实现这个接口
 * - Hilt 模块 [com.example.personalassistant.core.backup.di.BackupModule] 用 `@Binds` 绑接口到实现
 * - 生产代码不需改业务逻辑，VM 把依赖类型从具体类换成接口
 * - [com.example.personalassistant.core.backup.importer.BackupImporter] 内部仍依赖 [BackupValidator]（具体类），
 *   不受本次接口化影响
 */
interface IBackupValidator {
    /**
     * 校验 ZIP 流 + 抽取 manifest / checksums / 表 JSON 字节
     *
     * @param zipIn ZIP 输入流（消费完毕会关闭）
     * @param onPayload 回调（每个条目调用一次），返回 true 表示继续读，返回 false 表示跳过该条目
     * @return [com.example.personalassistant.core.backup.model.BackupResult.Success] 携带 [ValidationBundle]，
     *         或 [com.example.personalassistant.core.backup.model.BackupResult.Failure] 携带错误码
     */
    fun validateAndExtract(
        zipIn: ZipInputStream,
        onPayload: (entryName: String, bytes: ByteArray) -> Boolean = { _, _ -> true },
    ): com.example.personalassistant.core.backup.model.BackupResult<ValidationBundle>
}