package com.example.personalassistant.feature.finance.domain.model

/**
 * Phase 7a：交易类型（支出 / 收入）
 *
 * - 与 DB [com.example.personalassistant.feature.finance.data.local.TransactionEntity.type] 对应：
 *   - [EXPENSE] = 0
 *   - [INCOME]  = 1
 * - 持久化只走 [storageValue] / [fromStorageValue]，UI/UseCase 永远操作 enum。
 */
enum class TransactionType(val storageValue: Int) {
    EXPENSE(0),
    INCOME(1);

    companion object {
        /** 未知值（数据库脏数据兜底）默认按支出处理 */
        val DEFAULT: TransactionType = EXPENSE

        fun fromStorageValue(value: Int): TransactionType =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
