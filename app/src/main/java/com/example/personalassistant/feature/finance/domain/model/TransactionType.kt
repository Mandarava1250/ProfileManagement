package com.example.personalassistant.feature.finance.domain.model

/**
 * Phase 7a：交易类型（支出 / 收入）
 *
 * - 与 DB [com.example.personalassistant.feature.finance.data.local.TransactionEntity.type] 对应：
 *   - [EXPENSE] = 0
 *   - [INCOME]  = 1
 * - 持久化只走 [storageValue] / [fromStorageValue]，UI/UseCase 永远操作 enum。
 *
 * ## Phase 5：跨账户转账（新增 2 个值）
 * - [TRANSFER_OUT] / [TRANSFER_IN] 是**同一笔转账的两条配对流水**，
 *   靠 `TransactionEntity.transferAccountId` 互相指认对方账户。
 * - 两者都**不是**支出也不是收入：全部统计 SQL 都写死 `type = 0` / `type = 1`
 *   或 `CASE WHEN type = 0/1`，所以转账天然不进支出/收入/月度报表/零钱池，
 *   一条查询都不用改。这是选「新增 enum 值」而不是「复用支出+收入」的决定性理由。
 * - ⚠️ 只有 [EXPENSE] 会扣零钱池，[TRANSFER_OUT] 不扣 —— 它扣的是**账户余额**，
 *   余额是派生值（见 [com.example.personalassistant.feature.profile.data.local.UserProfileEntity.initialBalance]）。
 */
enum class TransactionType(val storageValue: Int) {
    EXPENSE(0),
    INCOME(1),

    /** 转出：本账户 → 对方账户，只减本账户余额，不算支出 */
    TRANSFER_OUT(2),

    /** 转入：对方账户 → 本账户，只增本账户余额，不算收入 */
    TRANSFER_IN(3);

    /**
     * 是否转账类型（余额计算、统计排除、UI 分组都靠它）。
     *
     * 写成枚举成员而不是 companion 里的扩展属性：扩展属性定义在 companion 内时，
     * 调用方必须 `import ...TransactionType.Companion.isTransfer` 才能用，
     * 而成员属性在任何拿到该枚举的地方都能 `type.isTransfer` 直接调用。
     */
    val isTransfer: Boolean
        get() = this == TRANSFER_OUT || this == TRANSFER_IN

    companion object {
        /** 未知值（数据库脏数据兜底）默认按支出处理 */
        val DEFAULT: TransactionType = EXPENSE

        fun fromStorageValue(value: Int): TransactionType =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT

        /**
         * 参与「支出/收入」这类普通记账流水的类型。
         *
         * 为什么要把这个判断做成显式集合，而不是 `type != TRANSFER_*` 散落各处：
         * 记账页的类型选择器（新增/编辑流水）根本不该出现转账 —— 转账必须成对写入
         * 两个账户，走不了 `addTransaction(type, ...)` 那条只认当前账户的路。
         * 有了本集合，UI 可以直接 `TransactionType.manualTypes` 渲染分段按钮，
         * 少一处「忘了过滤」的风险。
         */
        val manualTypes: List<TransactionType> = listOf(EXPENSE, INCOME)

        /** 转账配对类型（Phase 5） */
        val transferTypes: List<TransactionType> = listOf(TRANSFER_OUT, TRANSFER_IN)
    }
}
