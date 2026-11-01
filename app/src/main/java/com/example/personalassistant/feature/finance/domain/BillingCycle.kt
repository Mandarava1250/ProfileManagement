package com.example.personalassistant.feature.finance.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 记账账期（billing cycle）计算
 *
 * ## 「月截止日」是什么
 *
 * **截止日 = 账期的分界点**。用户可在设置里选 1-28 号：
 *
 * ```
 * 截止日 = 1 号：   10-01 ~ 11-01      11-01 ~ 12-01
 * 截止日 = 15 号：  09-15 ~ 10-14     10-15 ~ 11-14     11-15 ~ 12-14
 * ```
 *
 * ## ⚠️ 为什么区间是「连续切分」而不是「每段各自算两端」
 *
 * 直觉写法是"段 ym 的两端都用 ym 自己的截止日"。但那样切分点会**错位**：
 *
 * ```
 * 段 "2026-10"（截止日 1）  = [10-01, 11-01)
 * 段 "2026-11"（截止日 15） = [11-15, 12-15)
 *                             ↑ 11-01 ~ 11-14 这 14 天的流水谁都查不到
 * ```
 *
 * 用户的账会对不上（这 14 天的支出不在任何账期报表里），所以**不能这么实现**。
 *
 * 本实现改为**上一段的终点 = 下一段的起点**：
 *
 * ```
 * 段 ym = [ym.atDay(cutoffFor(ym)), (ym+1).atDay(cutoffFor(ym+1)))
 * ```
 *
 * 边界自动咬合，**任何一天都恰好属于一个账期**。
 * 代价：改截止日时，紧邻生效点的那个段会被拉长/缩短（边界移动），
 * 更早的段完全不受影响。
 *
 * ## yearMonth 标签
 *
 * 标签 = **该段起始所在的自然月**：
 * - 段 `10-15 ~ 11-14` 标为 `"2026-10"`
 * - 段 `09-15 ~ 10-14` 标为 `"2026-09"`
 *
 * ## 为什么上限是 28
 *
 * 2 月只有 28 天。29/30/31 号在 2 月不存在，`YearMonth.atDay(29)` 会抛异常。
 * 所以截止日硬性限制在 1..28。
 */
object BillingCycle {

    /** 截止日下限 */
    const val MIN_CUTOFF_DAY = 1

    /** 截止日上限（2 月只有 28 天） */
    const val MAX_CUTOFF_DAY = 28

    /** 默认截止日 = 1 号（= 自然月，与本功能上线前行为一致） */
    const val DEFAULT_CUTOFF_DAY = 1

    private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** 把任意输入夹到合法区间 */
    fun clampCutoff(day: Int): Int = day.coerceIn(MIN_CUTOFF_DAY, MAX_CUTOFF_DAY)

    /**
     * 账期区间（左闭右开，**连续切分**）
     *
     * @param ym 该段的标签（起始所在自然月），格式 "yyyy-MM"
     * @param policy 截止日策略，提供每个 ym 各自生效的截止日
     * @return `start` 到 `endExclusive` 的毫秒时间戳
     */
    fun rangeOf(
        ym: String,
        policy: MonthCutoffPolicy,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long, Long> {
        val ymObj = YearMonth.parse(ym)
        val nextYm = ymObj.plusMonths(1).toString()
        val start = startDateOf(ym, policy).atStartOfDay(zone).toInstant().toEpochMilli()
        // 终点 = 下一段的起点 → 保证连续，不留缝、不重叠
        val endExclusive = startDateOf(nextYm, policy).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to endExclusive
    }

    /** 段 ym 的起始日期 */
    fun startDateOf(ym: String, policy: MonthCutoffPolicy): LocalDate =
        YearMonth.parse(ym).atDay(policy.cutoffFor(ym))

    /** 段 ym 的结束日期（含，UI 展示用） */
    fun endDateOf(ym: String, policy: MonthCutoffPolicy): LocalDate =
        startDateOf(YearMonth.parse(ym).plusMonths(1).toString(), policy).minusDays(1)

    /**
     * 某个日期落在哪个账期（返回该账期的 ym 标签）
     *
     * 候选只有两个：日期所在自然月、或它的上一个月
     * （因为截止日 ∈ 1..28，段的起点在 ym 的前 28 天内，终点在 ym+1 的前 28 天内）。
     */
    fun yearMonthOf(
        date: LocalDate,
        policy: MonthCutoffPolicy,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val natural = YearMonth.from(date)
        val thisYm = natural.toString()
        val (start, end) = rangeOf(thisYm, policy, zone)
        val ts = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return if (ts in start until end) thisYm else natural.minusMonths(1).toString()
    }

    /**
     * 距离本账期结束还有几天（**含今天**）
     *
     * 截止日 = 1 号时，等价于旧算法 `daysInMonth - dayOfMonth + 1`。
     */
    fun remainingDays(
        date: LocalDate,
        policy: MonthCutoffPolicy,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val ym = yearMonthOf(date, policy, zone)
        val (_, endExclusive) = rangeOf(ym, policy, zone)
        val todayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return ((endExclusive - todayStart) / ONE_DAY_MS).toInt().coerceAtLeast(1)
    }

    /**
     * UI 展示用的账期区间（**闭区间**）
     *
     * 内部是左闭右开 `[start, endExclusive)`，展示时末位要减一天。
     */
    fun displayRange(ym: String, policy: MonthCutoffPolicy): String {
        val start = startDateOf(ym, policy)
        val end = endDateOf(ym, policy)
        return "${start.format(ISO_DATE)} ~ ${end.format(ISO_DATE)}"
    }
}
