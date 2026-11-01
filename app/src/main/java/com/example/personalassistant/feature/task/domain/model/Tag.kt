package com.example.personalassistant.feature.task.domain.model

/**
 * Phase 6c：标签（Domain 模型）
 *
 * - id：DB 主键（0 = 新建未持久化）
 * - nameNormalized：小写规范化名（DB UNIQUE 去重键），如 "work"
 * - displayName：UI 显示用的原始名（保留用户输入的大小写），如 "Work"
 * - colorHex：ARGB hex 颜色值（如 0xFFFF69B4），从 [com.example.personalassistant.feature.task.presentation.util.TagPalette] 选
 * - colorSource：调色板来源标识（默认 "default"，未来可扩展）
 * - createdAt：创建时间（毫秒）
 * - usageCount：当前被多少任务使用（0 表示尚未被任何任务使用）
 *
 * 设计动机：
 * - 标签是全局共享的（不属于任何任务），所以 id 是稳定的全局主键
 * - displayName 与 nameNormalized 分离，DB 层做去重（索引列），UI 层保留用户原始输入
 */
data class Tag(
    val id: Long = 0L,
    val nameNormalized: String,
    val displayName: String,
    val colorHex: Long,
    val colorSource: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 0,
) {
    /**
     * 计算规范化名（trim + lowercase + 折叠首尾空格）。
     *
     * 写入 DB 前必须用此函数，避免「Work」和「 work」被视为不同标签。
     */
    companion object {
        fun normalize(input: String): String = input.trim().lowercase()
    }
}