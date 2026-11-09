package com.example.personalassistant.feature.finance.domain.model

/**
 * 批次 D：一次日切结算的结果
 *
 * @param settledDate 本次结算到哪一天（当天 00:00 时间戳）
 * @param settledDays 本次实际结算了几天（没 `daily_pools` 行的日子不计）
 * @param settledAmount 本次累加进余额的金额（可正可负）
 * @param balance 结算后的零钱池余额
 */
data class CoinPoolSettlement(
    val settledDate: Long,
    val settledDays: Int,
    val settledAmount: Double,
    val balance: Double,
)
