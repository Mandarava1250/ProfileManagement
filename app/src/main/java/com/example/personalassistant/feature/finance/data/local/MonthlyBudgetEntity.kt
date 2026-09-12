package com.example.personalassistant.feature.finance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personalassistant.feature.profile.data.local.AccountIds
import kotlinx.serialization.Serializable

/**
 * 月度预算实体
 *
 * - accountId: 所属账户（Phase 3）
 * - yearMonth 格式 "yyyy-MM"，每账户每月一条记录
 * - isAutoRefresh: 是否每天自动刷新零钱池
 * - carryOverEnabled: 昨日剩余是否结转
 */
@Entity(
    tableName = "monthly_budgets",
    indices = [
        // Phase 3：唯一约束从 yearMonth 升级为 (accountId, yearMonth) ——
        // 只加列不改索引的话，第二个账户写同一个月会撞 UNIQUE 失败
        Index(value = ["accountId", "yearMonth"], unique = true)
    ]
)
@Serializable
data class MonthlyBudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 所属账户 id（Phase 3） */
    @ColumnInfo(name = "accountId", defaultValue = "1")
    val accountId: Long = AccountIds.DEFAULT_ACCOUNT_ID,

    /** 格式 "yyyy-MM" */
    @ColumnInfo(name = "yearMonth")
    val yearMonth: String,

    /** 月可支配总金额 */
    @ColumnInfo(name = "totalAmount")
    val totalAmount: Double,

    /** 是否启用动态日预算刷新 */
    @ColumnInfo(name = "isAutoRefresh")
    val isAutoRefresh: Boolean = true,

    /** 是否启用昨日结转 */
    @ColumnInfo(name = "carryOverEnabled")
    val carryOverEnabled: Boolean = true,

    /** 超支警告阈值，剩余金额低于此值时提示 */
    @ColumnInfo(name = "warningThreshold")
    val warningThreshold: Double = 0.0,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
)
