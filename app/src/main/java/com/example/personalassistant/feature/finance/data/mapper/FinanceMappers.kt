package com.example.personalassistant.feature.finance.data.mapper

import com.example.personalassistant.feature.finance.data.local.CategoryEntity
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.CategoryExpenseSum
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.DayExpenseSum
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType

/**
 * Phase 7a：Entity ↔ Domain 双向映射（finance 模块）
 *
 * - toDomain：DB 读出后立刻转 domain，避免 UI/UseCase 看到 Entity
 * - toEntity：DB 写入前转 Entity
 *
 * 设计取舍：Mapper 只做字段对应，不做业务校验（金额 > 0、分类存在等校验由 UseCase 层处理）。
 */

// ===== Transaction =====

internal fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = amount,
    type = TransactionType.fromStorageValue(type),
    categoryId = categoryId,
    date = date,
    note = note,
    createdAt = createdAt,
    source = source,
    attachmentUri = attachmentUri,
    // Phase 5：转账对方账户。transferPairId **不**映射到领域模型（理由见 Transaction 注释）
    transferAccountId = transferAccountId,
    // Phase 21：来源清单项（清单 ↔ 记账联动）
    linkedTaskId = linkedTaskId,
)

internal fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount,
    type = type.storageValue,
    categoryId = categoryId,
    date = date,
    note = note,
    createdAt = createdAt,
    source = source,
    attachmentUri = attachmentUri,
    // Phase 21：来源清单项
    linkedTaskId = linkedTaskId,
)

// ===== Category =====

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = TransactionType.fromStorageValue(type),
    iconName = iconName,
    color = color,
    isDefault = isDefault,
    sortOrder = sortOrder,
)

internal fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type.storageValue,
    iconName = iconName,
    color = color,
    isDefault = isDefault,
    sortOrder = sortOrder,
)

// ===== MonthlyBudget =====

internal fun MonthlyBudgetEntity.toDomain(): MonthlyBudget = MonthlyBudget(
    id = id,
    yearMonth = yearMonth,
    totalAmount = totalAmount,
    isAutoRefresh = isAutoRefresh,
    carryOverEnabled = carryOverEnabled,
    warningThreshold = warningThreshold,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun MonthlyBudget.toEntity(): MonthlyBudgetEntity = MonthlyBudgetEntity(
    id = id,
    yearMonth = yearMonth,
    totalAmount = totalAmount,
    isAutoRefresh = isAutoRefresh,
    carryOverEnabled = carryOverEnabled,
    warningThreshold = warningThreshold,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ===== DailyPool =====

internal fun DailyPoolEntity.toDomain(): DailyPool = DailyPool(
    id = id,
    date = date,
    dailyBudget = dailyBudget,
    remainingAmount = remainingAmount,
    totalSpentToday = totalSpentToday,
    totalIncomeToday = totalIncomeToday,
    carryOver = carryOver,
    lastUpdated = lastUpdated,
)

internal fun DailyPool.toEntity(): DailyPoolEntity = DailyPoolEntity(
    id = id,
    date = date,
    dailyBudget = dailyBudget,
    remainingAmount = remainingAmount,
    totalSpentToday = totalSpentToday,
    totalIncomeToday = totalIncomeToday,
    carryOver = carryOver,
    lastUpdated = lastUpdated,
)

// ===== 统计投影 → domain =====

/** FinanceDao.CategorySum → CategoryExpenseSum */
internal fun com.example.personalassistant.feature.finance.data.local.CategorySum.toDomain(): CategoryExpenseSum =
    CategoryExpenseSum(categoryId = categoryId, total = total)

/** FinanceDao.DayBucketSum → DayExpenseSum */
internal fun com.example.personalassistant.feature.finance.data.local.DayBucketSum.toDomain(): DayExpenseSum =
    DayExpenseSum(dayBucket = dayBucket, total = total)
