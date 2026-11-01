package com.example.personalassistant.core.backup.model

import kotlinx.serialization.Serializable

/**
 * Phase 8 Sub-step 4：每张表 JSON 的 sha256 校验码
 *
 * 写入 ZIP 顶层 checksums.json。key 是表名，value 是该表 JSON 内容的
 * sha256 hex 摘要。导入侧比对不一致则视为文件被篡改/损坏，拒绝导入。
 */
@Serializable
data class BackupChecksums(
    val checksums: Map<String, String>,
)
