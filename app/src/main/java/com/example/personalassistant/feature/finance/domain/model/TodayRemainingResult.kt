package com.example.personalassistant.feature.finance.domain.model

/**
 * 批次 C：「今日剩余」的完整派生结果
 *
 * ## 为什么打包成一个 data class 而不是拆成三个 StateFlow
 *
 * 三个值（金额 / 本月剩余 / 剩余天数）**必须同源同帧**：
 * 口径切一下、天数走一天、金额重算 —— 如果拆成三条流，
 * UI 有可能拿到「AUTO 口径的金额 + FIXED 口径的天数」这种拼凑出来的中间态。
 * 打包成一个对象 → 天然一致。
 *
 * ## 为什么放 domain 而不是 presentation
 *
 * 它是「今日剩余」这个领域概念的完整表达，不是某个页面的视图状态。
 * 参照 `TodayOverview`（日历模块）的做法 —— 展示模型放 presentation，
 * 但这个被多处复用（卡片 / 预算弹窗 / 未来的日历横条），放 domain 更合适。
 */
data class TodayRemainingResult(
    /** 当前口径 */
    val mode: TodayRemainingMode = TodayRemainingMode.DEFAULT,

    /** 今日剩余金额；null = 未设预算（UI 显示「未设置预算」） */
    val amount: Double? = null,

    /** 本月剩余额度（不分摊）；null = 未设预算 */
    val monthRemaining: Double? = null,

    /** 账期剩余天数（含今天，最少 1） */
    val remainingDays: Int = 0,
) {
    /** 是否超支（金额为负） */
    val isOverBudget: Boolean
        get() = amount != null && amount < 0.0
}
