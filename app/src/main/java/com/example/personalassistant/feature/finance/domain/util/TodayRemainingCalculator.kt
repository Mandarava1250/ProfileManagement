package com.example.personalassistant.feature.finance.domain.util

import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * 批次 C：「今日剩余」的纯计算
 *
 * ## 为什么是纯函数
 *
 * 「今日剩余」改成**派生值**之后，它就再也不该落库：
 * 落库的派生值 = 用户改一次预算、历史那一行也跟着变，用户月底对账时看到的数字全是假的。
 * 派生的东西每次现算，永远不可能和源数据不一致 ——
 * 这条原则在 `UserProfileEntity.initialBalance`（余额不落库）那里已经立过一次。
 *
 * 放在 domain 层（不是 presentation）是因为：
 * - 它要吃 `MonthCutoffPolicy`（账期口径），那是 domain 的概念；
 * - 它是纯计算 → JVM 单测毫秒级验证，不需要 Robolectric。
 *
 * ## 两种口径
 *
 * | 模式 | 结果 |
 * |---|---|
 * | [TodayRemainingMode.FIXED] | 本月可用余额 − 本月已支出 |
 * | [TodayRemainingMode.AUTO] | (本月可用余额 − 本月已支出) ÷ 账期剩余天数（含今天，最少 1 天） |
 *
 * ## 边界
 *
 * - 未设预算（[monthBudgetTotal] 为 null）→ 返回 **null**（UI 显示「未设置预算」，
 *   而不是显示 ¥0.00 —— 后者会让人以为「预算被设成 0 了」）
 * - 已超支 → 返回**负数**（不钳到 0）。钳掉的话用户就看不出超了多少。
 *   AUTO 模式下负数 = 「本月超了 X，按剩余 N 天摊下来每天还差 X/N」。
 * - 账期剩余天数最少 1 天（[BillingCycle.remainingDays] 自己保证，这里再兜一层）
 */
object TodayRemainingCalculator {

    /**
     * 今日剩余 = 本月剩余额度（可负）
     *
     * @param mode 显示口径
     * @param monthBudgetTotal 本月可用余额（`monthly_budgets.totalAmount`）；null = 未设预算
     * @param monthExpense 本月已支出（账期口径的 SUM）；null 按 0 处理
     * @param remainingDays 账期剩余天数（含今天）；小于 1 时按 1 处理
     * @return null = 未设预算；否则为今日剩余金额（可能为负）
     */
    fun compute(
        mode: TodayRemainingMode,
        monthBudgetTotal: Double?,
        monthExpense: Double?,
        remainingDays: Int,
    ): Double? {
        val total = monthBudgetTotal ?: return null
        val remaining = total - (monthExpense ?: 0.0)
        return when (mode) {
            TodayRemainingMode.FIXED -> remaining
            TodayRemainingMode.AUTO -> remaining / remainingDays.coerceAtLeast(1)
        }
    }

    /**
     * 今日剩余 = 本月剩余额度（可负），天数由 [BillingCycle.remainingDays] 现算
     *
     * 这是给 ViewModel 用的入口 —— 它需要把「今天」和「账期策略」传进来。
     * 之所以再包一层而不是让调用方自己算天数：天数口径（**账期**天数，不是自然月天数）
     * 是个容易搞错的东西（见 [BillingCycle] 的 KDoc），包一层就只有一处实现。
     *
     * @param today 今天（决定「还剩几天」）
     * @param policy 月截止日策略（决定账期怎么切）
     */
    fun compute(
        mode: TodayRemainingMode,
        monthBudgetTotal: Double?,
        monthExpense: Double?,
        today: LocalDate,
        policy: MonthCutoffPolicy,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Double? = compute(
        mode = mode,
        monthBudgetTotal = monthBudgetTotal,
        monthExpense = monthExpense,
        remainingDays = BillingCycle.remainingDays(today, policy, zone),
    )

    /**
     * 本月剩余额度（不分摊）
     *
     * 单独暴露是因为卡片副标题要展示它（无论当前是哪种口径），
     * 而且 FIXED 模式本身就是它。
     */
    fun monthRemaining(monthBudgetTotal: Double?, monthExpense: Double?): Double? {
        val total = monthBudgetTotal ?: return null
        return total - (monthExpense ?: 0.0)
    }
}
