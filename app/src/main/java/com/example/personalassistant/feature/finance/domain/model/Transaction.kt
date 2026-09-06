package com.example.personalassistant.feature.finance.domain.model

/**
 * Phase 7a：交易（一条账目）的领域模型
 *
 * 与 Entity 字段一一对应（去掉 ORM 注解 + 默认值简化）。
 *
 * - [date] 业务时间（用户记账那一刻），毫秒时间戳
 * - [createdAt] 入库时间，用于审计
 * - [source] 来源（保留字段，7a UI 固定显示"手动"）
 */
data class Transaction(
    val id: Long = 0L,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long,
    val date: Long,
    val note: String? = null,
    val createdAt: Long = 0L,
    val source: String = "手动",
    val attachmentUri: String? = null,
)
