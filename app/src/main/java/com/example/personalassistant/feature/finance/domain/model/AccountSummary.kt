package com.example.personalassistant.feature.finance.domain.model

/**
 * Phase 5：跨账户汇总用的领域模型
 *
 * 这些都是**只读投影**，由 FinanceRepository 从 DAO 聚合结果转换而来。
 * 放一个文件里而不是拆三个，是因为它们共同描述同一件事：
 * 「把所有账户的账本叠起来看」。
 */

/**
 * 单个账户的当月预算行（「按账户逐行列出」用）
 *
 * - [budgetAmount] = null 表示该账户**当月没设预算**，与「预算设成 0」是两件事。
 *   合计时必须跳过 null 行并把它们单独计数（见 [BudgetSummaryTotals.accountsMissingBudget]），
 *   否则总数看着对、其实少算了一个账户 —— 这类错不会崩，只会误导，是本项目最忌讳的静默错账。
 * - [transferIn] / [transferOut] 单独给，**不混进** [expense]：
 *   账户之间挪钱不是花钱，混进去月度报表就会凭空多出一笔不存在的消费。
 */
data class AccountBudgetSummary(
    val accountId: Long,
    val accountName: String,
    val budgetAmount: Double?,
    val expense: Double,
    val transferIn: Double,
    val transferOut: Double,
    val initialBalance: Double,
) {
    /** 未设预算 */
    val budgetMissing: Boolean get() = budgetAmount == null

    /** 当月剩余；未设预算时为 null（不能当 0 用） */
    val remaining: Double? get() = budgetAmount?.let { it - expense }

    /** 是否超支（未设预算时恒 false —— 没有预算就谈不上超） */
    val isOverBudget: Boolean get() = budgetAmount != null && expense > budgetAmount
}

/**
 * 跨账户合计（「合计卡」用）
 *
 * 语义上就是 [AccountBudgetSummary] 列表的折叠结果，但**故意保留**
 * [accountsMissingBudget] 这种「数据的缺口」而不是只给四个数：
 * 只给总数，用户无法知道这个总数是不是漏算了账户。
 */
data class BudgetSummaryTotals(
    val totalBudget: Double,
    val totalExpense: Double,
    val totalTransferIn: Double,
    val totalTransferOut: Double,
    val accountCount: Int,
    val accountsMissingBudget: Int,
) {
    /** 合计口径下的剩余（只统计**有预算**的账户） */
    val totalRemaining: Double get() = totalBudget - totalExpense

    /** 已用比例；预算全为空时 0 */
    val usedRatio: Double
        get() = if (totalBudget <= 0.0) 0.0 else totalExpense / totalBudget

    /**
     * 当月转账总额（取转入侧）。
     *
     * 转入与转出在**全账户口径**下应当相等（每笔转账两边各记一次），
     * 取任一侧都对；若不equal 说明数据被外部破坏（如手工导入了半对转账），
     * UI 用 [transferImbalanced] 提示，而不是偷偷取平均。
     */
    val totalTransfer: Double get() = totalTransferIn

    /** 转账两边对不上 = 数据异常信号 */
    val transferImbalanced: Boolean
        get() = kotlin.math.abs(totalTransferIn - totalTransferOut) > TRANSFER_BALANCE_TOLERANCE

    companion object {
        /** 浮点比较容差（金额单位元，1 分钱以内视为相等） */
        const val TRANSFER_BALANCE_TOLERANCE = 0.005

        fun from(rows: List<AccountBudgetSummary>): BudgetSummaryTotals = BudgetSummaryTotals(
            totalBudget = rows.sumOf { it.budgetAmount ?: 0.0 },
            totalExpense = rows.sumOf { it.expense },
            totalTransferIn = rows.sumOf { it.transferIn },
            totalTransferOut = rows.sumOf { it.transferOut },
            accountCount = rows.size,
            accountsMissingBudget = rows.count { it.budgetMissing },
        )
    }
}

/**
 * 单个账户的余额（派生值，**不落库**）
 *
 * 公式：`balance = initialBalance + income - expense + transferNet`
 *
 * 为什么把分量都留着而不是只算一个数：用户看到「余额 320」不会知道为什么，
 * 看到「期初 500 + 收入 200 − 支出 380 + 净转入 0」才能自己对上账。
 * 这也是派生余额相对「存一份 running balance」的真实优势 —— 它可以自证。
 */
data class AccountBalance(
    val accountId: Long,
    val accountName: String,
    val initialBalance: Double,
    val income: Double,
    val expense: Double,
    val transferNet: Double,
) {
    val balance: Double get() = initialBalance + income - expense + transferNet
}

/**
 * 转账目标账户选项（选择器用）
 *
 * 带 [balance] 是为了让 UI 能在选择时就显示「对方账户现有多少」，
 * 而不是等转完账才看到数字变化。
 */
data class TransferAccountOption(
    val accountId: Long,
    val accountName: String,
    val balance: Double,
)

/**
 * Phase 5：单账户余额分量（DAO 投影的领域形式）
 *
 * 只有流水聚合出来的三项，**不含期初** —— 期初在 `user_profiles`，
 * 两者分属两张表，拼装的活交给 [com.example.personalassistant.feature.finance.domain.usecase.ObserveAccountBalancesUseCase]，
 * 这样 FinanceRepository 不必依赖账户表 DAO（保持分层）。
 */
data class AccountBalanceSum(
    val accountId: Long,
    val expense: Double,
    val income: Double,
    val transferNet: Double,
)

/**
 * Phase 5：全账户合并的当月总额（「合计卡」）
 *
 * 与 [BudgetSummaryTotals] 的区别：这个来头是**流水侧**（收入/支出/转账总额），
 * 那个来头是**预算侧**（预算金额 + 逐账户行）。两张卡显示的数不同，
 * 所以不合成一个类——合完会变成一半字段永远用不上的胖对象。
 *
 * [transferIn] 与 [transferOut] 在全账户口径下应当相等（每笔转账两边各一行）；
 * 不相等就是数据异常（例如 CSV 导入只导进一半），UI 应提示而非静默取平均。
 */
data class GlobalMonthlySums(
    val expense: Double,
    val income: Double,
    val transferOut: Double,
    val transferIn: Double,
) {
    /** 结余 = 收入 − 支出（转账不影响结余，它只是钱换了个口袋） */
    val balance: Double get() = income - expense

    /** 转账两边对不上 */
    val transferImbalanced: Boolean
        get() = kotlin.math.abs(transferIn - transferOut) >
            BudgetSummaryTotals.TRANSFER_BALANCE_TOLERANCE
}
