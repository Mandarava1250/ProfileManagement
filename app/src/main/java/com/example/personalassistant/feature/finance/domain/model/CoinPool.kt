package com.example.personalassistant.feature.finance.domain.model

/**
 * 批次 D：零钱池领域模型（每日自动结算的累计余额）
 *
 * 批次 C 时它是「用户手动攒的一笔零用钱」；批次 D 起语义变了：
 * **余额 = 历次日切结算额之累加，用户无法自行更改**。
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
    /** 结算水位线：已结算到的最后一天（当天 00:00 时间戳）；0 = 从来没结算过 */
    val lastSettledDate: Long = 0L,
) {
    /** 是否欠账（余额为负） */
    val isNegative: Boolean get() = balance < 0.0

    /** 是否还没结算过（UI 显示「尚未结算」） */
    val hasSettled: Boolean get() = lastSettledDate > 0L
}
