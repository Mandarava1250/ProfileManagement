package com.example.personalassistant.feature.finance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personalassistant.feature.profile.data.local.AccountIds
import kotlinx.serialization.Serializable

/**
 * 每日零钱池实体（核心特色功能）
 *
 * - accountId: 所属账户（Phase 3）
 * - date: 当天 00:00 时间戳（按账户唯一，每条记录代表「该账户的那一天」）
 * - dailyBudget: 当日预算
 * - remainingAmount: 当前剩余（可为负数）
 * - totalSpentToday: 今日累计支出
 * - totalIncomeToday: 今日累计收入
 * - carryOver: 从昨日结转过来的金额
 *
 * 写入策略：消费 transaction 与 daily_pools 必须在同一 Room 事务中更新
 * （参考说明书 6.7 零钱池专项注意点）
 */
@Entity(
    tableName = "daily_pools",
    indices = [
        // Phase 3：唯一约束从 date 升级为 (accountId, date) ——
        // 否则 A/B 两账户的同一天撞 UNIQUE，零钱池直接写不进去
        Index(value = ["accountId", "date"], unique = true)
    ]
)
@Serializable
data class DailyPoolEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 所属账户 id（Phase 3） */
    @ColumnInfo(name = "accountId", defaultValue = "1")
    val accountId: Long = AccountIds.DEFAULT_ACCOUNT_ID,

    /** 当天 00:00 时间戳 */
    @ColumnInfo(name = "date")
    val date: Long,

    /** 当日预算 */
    @ColumnInfo(name = "dailyBudget")
    val dailyBudget: Double,

    /** 当前剩余金额（可为负数，表示超支）*/
    @ColumnInfo(name = "remainingAmount")
    val remainingAmount: Double,

    /** 今日累计支出 */
    @ColumnInfo(name = "totalSpentToday")
    val totalSpentToday: Double = 0.0,

    /** 今日累计收入 */
    @ColumnInfo(name = "totalIncomeToday")
    val totalIncomeToday: Double = 0.0,

    /** 昨日结转金额 */
    @ColumnInfo(name = "carryOver")
    val carryOver: Double = 0.0,

    /** 最后更新时间 */
    @ColumnInfo(name = "lastUpdated")
    val lastUpdated: Long = System.currentTimeMillis()
)
