package com.example.personalassistant.feature.finance.domain.model

/**
 * 批次 C：零钱池领域模型（手动存取的一笔零用钱）
 *
 * 与 [DailyPool] 是两回事 —— 后者是系统按预算每天发的额度快照。
 * 详细对照见 `com.example.personalassistant.feature.finance.data.local.CoinPoolEntity` 的表格。
 */
data class CoinPool(
    val id: Long = 0L,
    val accountId: Long,
    val balance: Double,
    val note: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** 是否欠账（余额为负） */
    val isNegative: Boolean get() = balance < 0.0
}
