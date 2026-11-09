package com.example.personalassistant.feature.finance.domain.util

/**
 * 批次 D：零钱池「当日净结余」的纯计算
 *
 * ## 口径（伙伴已拍板）
 *
 * ```
 * settleAmount(D) = daily_pools[D].dailyBudget
 *                 + daily_pools[D].totalIncomeToday
 *                 - daily_pools[D].totalSpentToday
 * ```
 *
 * 也就是「今天发了多少额度 + 今天赚了多少 − 今天花了多少」。
 *
 * ## 为什么用「当日净结余」而不是「按月口径现算」
 *
 * 现算口径（`(月预算 − 月支出) ÷ 账期剩余天数`）有两个硬伤：
 *
 * 1. **重复计入**：月级数据逐日累加 = 同一笔月预算被加了 N 次，余额按 N² 涨。
 * 2. **补算失真**：App 几天没开，一次补存好几天的额度。
 *
 * 「当日净结余」只吃 `daily_pools` 里**那一天那一行**：
 * - 那天用多少就是多少，不会重复计入；
 * - 那天没有 `daily_pools` 行 → 当天没参与预算执行 → 不产生结转（天然正确补算）；
 * - 结算额一旦落库就不再变 —— 用户事后改本月预算，历史结算**不跟着变**。
 *
 * ## 为什么是纯函数
 *
 * 跟 [TodayRemainingCalculator] 一个道理：纯计算 → JVM 单测毫秒级验证，
 * 不需要 Robolectric，也不需要数据库。
 */
object CoinPoolSettlementCalculator {

    /**
     * 当日结算额（可负）
     *
     * @param dailyBudget 当天系统发的日额度（`daily_pools.dailyBudget`）
     * @param totalIncomeToday 当天累计收入（`daily_pools.totalIncomeToday`）
     * @param totalSpentToday 当天累计支出（`daily_pools.totalSpentToday`）
     * @return 当日净结余（正 = 有结余存入；负 = 超支，累加到余额上会把余额拉低）
     */
    fun settleAmount(
        dailyBudget: Double,
        totalIncomeToday: Double,
        totalSpentToday: Double,
    ): Double = dailyBudget + totalIncomeToday - totalSpentToday
}
