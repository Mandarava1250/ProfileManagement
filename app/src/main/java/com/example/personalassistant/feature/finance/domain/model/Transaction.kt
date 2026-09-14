package com.example.personalassistant.feature.finance.domain.model

/**
 * Phase 7a：交易（一条账目）的领域模型
 *
 * 与 Entity 字段一一对应（去掉 ORM 注解 + 默认值简化）。
 *
 * - [date] 业务时间（用户记账那一刻），毫秒时间戳
 * - [createdAt] 入库时间，用于审计
 * - [source] 来源（保留字段，7a UI 固定显示"手动"）
 *
 * ## Phase 5
 * - [type] 可能是 [TransactionType.TRANSFER_OUT] / [TRANSFER_IN]（跨账户转账）
 * - [transferAccountId] 转账对方账户 id；普通收支为 null。
 *   UI 用它显示「转入自 / 转出至 <账户名>」，不用 JOIN 查表。
 * - **不带** transferPairId：那个只服务配对删除，Repository 直接操作 Entity 就够，
 *   放进领域模型等于让 UI 层多一个它永远用不到、还可能误用的字段。
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
    /** 转账对方账户 id（Phase 5）；普通收支为 null */
    val transferAccountId: Long? = null,
)
