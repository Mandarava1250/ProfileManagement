package com.example.personalassistant.feature.finance.domain.model

/**
 * 批次 C：「今日剩余」的两种显示口径
 *
 * ## 为什么有两种
 *
 * 「今日剩余」这个数字有两种同样合理的理解，想要哪种是**个人偏好**：
 * - [FIXED]：本月一共还能花多少 —— 固定显示本月剩余额度
 * - [AUTO]：今天该花多少 —— 按本账期剩余天数把剩余额度均摊
 *
 * 举例：本月预算 3000，已花 1000，账期还剩 10 天
 * - FIXED → 2000（本月一共还能花 2000）
 * - AUTO  → 200（今天建议花 200）
 *
 * ## 为什么存 DataStore 而不是 Room
 *
 * 这是**显示口径**，不是账本状态。写进 `monthly_budgets` / `daily_pools` 会污染备份语义
 * （那两张表是「当时的决定」的快照），而且「清除全部数据」时用户会连这个偏好一起丢掉。
 * 参照 `CalendarDisplayRepository` / `HabitDisplayRepository` 的做法 —— 走 DataStore。
 */
enum class TodayRemainingMode {
    /** 固定：显示本月剩余额度（本月可用余额 − 本月已支出） */
    FIXED,

    /** 自动：按账期剩余天数均摊（今日可用 = 本月剩余 ÷ 剩余天数） */
    AUTO;

    /** UI 展示名。集中在这里，避免卡片和预算弹窗两处文案漂移。 */
    val displayName: String
        get() = when (this) {
            FIXED -> "固定"
            AUTO -> "自动"
        }

    companion object {
        /**
         * 默认口径 = [FIXED]
         *
         * 为什么默认固定而不是自动：固定口径**永远等于**「本月可用余额 − 本月已支出」，
         * 与预算弹窗里看到的数字对得上，用户不需要心算；
         * 自动口径多一步除法，首屏就出现一个用户没主动选过的数字容易引发困惑。
         */
        val DEFAULT: TodayRemainingMode = FIXED

        /** 从 DataStore 里存的字符串解析；认不出来时回落到 [DEFAULT] */
        fun fromStorageValue(raw: String?): TodayRemainingMode =
            when (raw) {
                FIXED.name -> FIXED
                AUTO.name -> AUTO
                else -> DEFAULT
            }
    }
}
