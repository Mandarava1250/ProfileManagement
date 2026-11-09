package com.example.personalassistant.feature.finance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personalassistant.feature.profile.data.local.AccountIds
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import kotlinx.serialization.Serializable

/**
 * 批次 D：零钱池日切结算流水（一行 = 某一天的结算）
 *
 * ## 为什么要有这张表
 *
 * 零钱池余额是**累加**出来的：`余额 = 上一次结算后的余额 + 当天的结算额`。
 * 累加本身不可逆 —— 余额看不出「这 100 块是哪天存进来的」。
 * 所以每次结算都落一行，把「哪一天、结了多少」记下来：
 *
 * - **幂等**：`(accountId, date)` 唯一 → 同一天重复结算会撞唯一约束，
 *   配合 `coin_pools.lastSettledDate` 水位线，保证一天只结算一次。
 * - **可审计**：`ym` 记下这笔结算属于哪个账期（账期可能跨自然月，不能按自然月推）。
 * - **历史不被篡改**：结算额一旦落库就不再变 —— 用户事后改本月预算，
 *   已经结算过的那些天**不会**跟着变（那是当时的决定）。
 *
 * ## 与 `coin_pools` 的关系
 *
 * | | `coin_pools` | `coin_pool_settlements`（本表） |
 * |---|---|---|
 * | 语义 | 累计余额（当前值） | 每日结算额（流水） |
 * | 粒度 | 每账户一行 | 每天一行 |
 * | 谁写 | 系统结算（累加） | 系统结算（追加） |
 *
 * ⚠️ 与 `daily_pools`（说明书 6.7 的「每日零钱池」）是三张不同的表，别混。
 */
@Entity(
    tableName = "coin_pool_settlements",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        // 一账户一天只结算一次 —— 这条唯一索引就是幂等的最后一道闸
        Index(value = ["accountId", "date"], unique = true),
    ],
)
@Serializable
data class CoinPoolSettlementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 所属账户 id（→ user_profiles.id，删账户连带删本行） */
    @ColumnInfo(name = "accountId", defaultValue = "1")
    val accountId: Long = AccountIds.DEFAULT_ACCOUNT_ID,

    /** 结算的那一天（当天 00:00 时间戳） */
    @ColumnInfo(name = "date")
    val date: Long,

    /** 当日结算额（可负 = 当天超支） */
    @ColumnInfo(name = "amount")
    val amount: Double,

    /** 这笔结算所属的账期标签（如 "2026-08"；账期可能跨自然月） */
    @ColumnInfo(name = "ym")
    val ym: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 批次 D：一天的结算计划（传给 [FinanceDao.settleCoinPool]）
 *
 * @param date 结算的那一天（当天 00:00 时间戳）
 * @param ym 这笔结算所属的账期标签（如 "2026-08"）
 */
data class CoinPoolSettlementPlan(
    val date: Long,
    val ym: String,
)

/**
 * 批次 D：一次结算的结果
 *
 * @param balance 结算后的零钱池余额
 * @param settledDays 本次实际结算了几天
 * @param settledAmount 本次累加进余额的金额（可正可负）
 * @param lastSettledDate 结算后的水位线（已结算到的最后一天）
 */
data class CoinPoolSettleOutcome(
    val balance: Double,
    val settledDays: Int,
    val settledAmount: Double,
    val lastSettledDate: Long,
)
