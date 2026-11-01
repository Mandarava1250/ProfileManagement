package com.example.personalassistant.feature.finance.domain.model

/**
 * Phase 7a：记账分类领域模型
 *
 * - [type] 0=支出分类，1=收入分类
 * - [isDefault] true=内置分类（不可删除，可改名）
 * - [iconName] Material Icon 名称字符串，由 UI 层 [IconRegistry] 解析成 ImageVector
 * - [color] ARGB Int（0xFF6750A4 格式）
 * - [sortOrder] 数字越小越靠前
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val type: TransactionType,
    val iconName: String = "Category",
    val color: Int = 0xFF6750A4.toInt(),
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
)
