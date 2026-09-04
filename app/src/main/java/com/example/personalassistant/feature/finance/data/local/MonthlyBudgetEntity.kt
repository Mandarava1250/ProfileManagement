package com.example.personalassistant.feature.finance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 月度预算实体
 *
 * - yearMonth 格式 "yyyy-MM"，每月独立一条记录
 * - isAutoRefresh: 是否每天自动刷新零钱池
 * - carryOverEnabled: 昨日剩余是否结转
 */
@Entity(
    tableName = "monthly_budgets",
    indices = [
        Index(value = ["yearMonth"], unique = true)
    ]
)
data class MonthlyBudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

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
