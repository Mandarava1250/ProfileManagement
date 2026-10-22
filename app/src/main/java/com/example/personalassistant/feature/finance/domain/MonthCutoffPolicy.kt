package com.example.personalassistant.feature.finance.domain

import java.time.YearMonth

/**
 * 月截止日策略
 *
 * ## 两个字段
 *
 * - [cutoffDay]：用户选的截止日（1-28）
 * - [effectiveFromYm]：这个截止日从哪个 ym 开始生效（格式 "yyyy-MM"）
 *
 * ## 为什么需要「生效 ym」
 *
 * 改了截止日之后，**更早的账期不应该跟着变**。所以要记住"新规则从哪个月开始"：
 *
 * ```
 * 10-20 把截止日从 1 改成 15
 *   → effectiveFromYm = "2026-10"（当前账期）
 *   → "2026-08" 及更早：仍按 1 号切，区间一模一样
 *   → "2026-10" 起：按 15 号切
 * ```
 *
 * 生效 ym 之前的那一段（"2026-09"）**终点会被推后**（10-01 → 10-14），
 * 因为账期是连续切分的（见 [BillingCycle] 的说明）—— 这是"边界移动"，
 * 不是"数据丢失"：任何一天都仍然恰好属于一个账期。
 *
 * ## 为什么不记"变更历史"
 *
 * 只记"当前值 + 生效 ym"两段。代价是**第三次**修改会覆盖第二次的记录
 * （第二次那段会退回按 1 号算）。低频操作，UI 上写明即可。
 */
data class MonthCutoffPolicy(
    /** 用户选的截止日 */
    val cutoffDay: Int = BillingCycle.DEFAULT_CUTOFF_DAY,
    /** 新截止日从哪个 ym 开始生效（"yyyy-MM"） */
    val effectiveFromYm: String = DEFAULT_EFFECTIVE_FROM,
) {

    /**
     * 某个 ym 账期用哪个截止日
     *
     * 生效 ym 之前 → 1 号（历史不变）；之后 → 用户选的值
     */
    fun cutoffFor(ym: String): Int =
        if (ym >= effectiveFromYm) {
            BillingCycle.clampCutoff(cutoffDay)
        } else {
            BillingCycle.DEFAULT_CUTOFF_DAY
        }

    /** 这个策略是不是"没改过"（默认 1 号） */
    val isDefault: Boolean
        get() = cutoffDay == BillingCycle.DEFAULT_CUTOFF_DAY &&
            effectiveFromYm <= DEFAULT_EFFECTIVE_FROM

    companion object {
        /** 永远早于任何真实 ym 的哨兵值 */
        const val DEFAULT_EFFECTIVE_FROM = "1970-01"

        /**
         * 用户改动截止日时写入的「生效 ym」
         *
         * 取**当前账期**（不是下个月）—— 因为用户改设置的那一刻，
         * 心里想的是"从现在起按新规则切"。
         *
         * @param currentCycleYm 当前所在账期的 ym 标签
         */
        fun effectiveFromFor(currentCycleYm: String): String =
            YearMonth.parse(currentCycleYm).toString()
    }
}
