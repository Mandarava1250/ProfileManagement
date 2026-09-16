package com.example.personalassistant.feature.finance.presentation.transfer

import com.example.personalassistant.feature.finance.domain.model.AccountBalance
import com.example.personalassistant.feature.finance.domain.model.TransferAccountOption

/**
 * Phase 9b D1：转账表单的**纯状态**（无 UI、无副作用，可直接 JVM 单测）。
 *
 * ## 为什么要把状态抠出来
 *
 * 原实现把 `targets / from / selected / amount / overBalance / canSubmit` 全写在
 * [TransferSheet] 的 Composable 体里（`remember` + 局部 val）。这些推导**与渲染无关**，
 * 却因为长在 Composable 里而只能靠 instrumented 测试碰 —— 8m 一轮，改一个边界要重跑全量。
 *
 * 抠出来之后：
 * - 推导规则（余额不足判定、自身排除、可提交条件）在 JVM 上毫秒级验证
 * - [TransferSheet] 只负责「把状态画出来 + 把事件抛出去」，instrumented 测试只验交互
 *
 * 与 `AccountSwitcherBar`（9b B2）同一套路子：**state in / callback out**。
 *
 * ## 为什么转出方不在状态里
 *
 * 转出方恒为当前账户 —— 这是刻意设计（见 [TransferSheet] 类文档：允许选转出方
 * 等于在多账户页面上开出越权路径）。所以状态里只需要 [fromName] / [fromBalance]
 * 用于文案展示，不需要「可选的转出账户列表」。
 */
data class TransferFormState(
    /** 可选的转入账户（已排除当前账户），复用领域层既有模型 */
    val targets: List<TransferAccountOption> = emptyList(),
    /** 转出方显示名；当前账户还没进余额列表时为 null */
    val fromName: String? = null,
    /** 转出方余额；未知时为 null */
    val fromBalance: Double? = null,
    val amountText: String = "",
    val note: String = "",
    /** 已选中的转入账户 id；未选为 null */
    val targetId: Long? = null,
) {
    /** 解析后的金额；非法输入（空 / 非数字 / 负数）为 null */
    val amount: Double? get() = amountText.trim().toDoubleOrNull()?.takeIf { it > 0.0 }

    /** 是否只剩一个账户（转账无处可去） */
    val hasNoTarget: Boolean get() = targets.isEmpty()

    /**
     * 是否超出转出方余额。
     *
     * 这是 UI 的前置提示；**真正的防线仍是 Repository 层校验** ——
     * 这里只是把「转不出去」提前说清楚，避免用户填完才发现。
     */
    val overBalance: Boolean
        get() {
            val a = amount ?: return false
            val from = fromBalance ?: return false
            return from < a
        }

    /** 选中的转入账户（用于「确认后」摘要行） */
    val selected: TransferAccountOption? get() = targets.firstOrNull { it.accountId == targetId }

    /** 提交按钮可用条件：有目标账户 + 金额合法 + 未超余额 */
    val canSubmit: Boolean get() = targets.isNotEmpty() && amount != null && targetId != null && !overBalance

    /** 确认摘要文案（选中后才显示）：`从 A −100 → B` */
    fun confirmSummary(): String? {
        val to = selected ?: return null
        return "确认后：${fromName ?: "当前账户"} −${amountText.trim()} → ${to.accountName}"
    }

    companion object {
        /**
         * 从余额列表构造表单状态（唯一入口，UI 与测试共用同一套推导）。
         *
         * @param balances `FinanceViewModel.accountBalances` 的输出
         * @param currentAccountId 当前账户 id（转出方）
         */
        fun from(
            balances: List<AccountBalance>,
            currentAccountId: Long,
            amountText: String = "",
            note: String = "",
            targetId: Long? = null,
        ): TransferFormState {
            val from = balances.firstOrNull { it.accountId == currentAccountId }
            return TransferFormState(
                // 排掉当前账户：自己转给自己在 Repository 层会被拒（SELF_TRANSFER），
                // 但把不可能选项摆在眼前仍然更差 —— 用户点了才知道不行，等于让人试错。
                targets = balances
                    .filter { it.accountId != currentAccountId }
                    .map { TransferAccountOption(it.accountId, it.accountName, it.balance) },
                fromName = from?.accountName,
                fromBalance = from?.balance,
                amountText = amountText,
                note = note,
                targetId = targetId,
            )
        }
    }
}