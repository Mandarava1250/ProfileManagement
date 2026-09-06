package com.example.personalassistant.feature.finance.domain.model

/**
 * Phase 7a：月度预算领域模型
 *
 * - [yearMonth] 格式 "yyyy-MM"，每月独立一条记录
 * - [isAutoRefresh] 是否每天自动刷新零钱池（7a 默认 true）
 * - [carryOverEnabled] 昨日剩余是否结转到今日（7a 默认 true）
 * - [warningThreshold] 超支警告阈值，剩余金额低于此值时提示（7a 仅落库，UI 简单展示）
 */
data class MonthlyBudget(
    val id: Long = 0L,
    val yearMonth: String,
    val totalAmount: Double,
    val isAutoRefresh: Boolean = true,
    val carryOverEnabled: Boolean = true,
    val warningThreshold: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
