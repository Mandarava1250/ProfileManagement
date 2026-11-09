package com.example.personalassistant.feature.habit.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 23：习惯模块的日期工具
 *
 * 习惯的「一天」用**当天 00:00 的毫秒时间戳**表示（与 `habit_checkins.date` 列一致）。
 *
 * ## 为什么不用「毫秒 + 86400000」推算前一天
 *
 * 夏令时切换那天一天只有 23 / 25 小时，直接加减固定毫秒会把日界算错 ——
 * 连续天数会在换季那天莫名其妙断掉。统一走 [LocalDate] 的日期运算，
 * 再按当地时区换算成毫秒。
 */
object HabitDates {

    /** 当天 00:00 的毫秒时间戳 */
    fun todayStartMillis(): Long = startOfDayMillis(LocalDate.now())

    /** [date] 当天 00:00 的毫秒时间戳 */
    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 毫秒时间戳 → 当地日期 */
    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** 所在周的周一 00:00（周一为一周之始，与 ISO-8601 一致） */
    fun startOfWeekMillis(date: LocalDate): Long = startOfDayMillis(mondayOf(date))

    /** 该日期所在周的周一 */
    fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    /** 某年 1 月 1 日 00:00 */
    fun startOfYearMillis(year: Int): Long = startOfDayMillis(LocalDate.of(year, 1, 1))

    /**
     * 某年 12 月 31 日 23:59:59.999（闭区间上界）
     *
     * 用「年末 + 24h - 1ms」兜底而不是精确的日末时刻：
     * 夏令时那天日末会提前/推后一小时，取宽一点的上界只会多包含 0 条记录，不会漏。
     */
    fun endOfYearMillis(year: Int): Long =
        startOfDayMillis(LocalDate.of(year, 12, 31)) + 24 * 60 * 60 * 1000L - 1

    /** 某年某月（1..12）的闭区间毫秒范围 */
    fun monthRangeMillis(year: Int, month: Int): Pair<Long, Long> {
        val first = LocalDate.of(year, month, 1)
        val last = first.withDayOfMonth(first.lengthOfMonth())
        val start = startOfDayMillis(first)
        val end = startOfDayMillis(last) + 24 * 60 * 60 * 1000L - 1
        return start to end
    }
}
