package com.example.personalassistant.feature.habit.domain.util

import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitCheckIn
import com.example.personalassistant.feature.habit.domain.model.HabitDayCell
import com.example.personalassistant.feature.habit.domain.model.HabitHeatmapCell
import com.example.personalassistant.feature.habit.domain.model.HabitHeatmapWeek
import com.example.personalassistant.feature.habit.domain.model.HabitStats
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType
import com.example.personalassistant.feature.habit.domain.model.StreakUnit
import java.time.LocalDate

/**
 * Phase 23：习惯统计的**纯函数**集合
 *
 * 全部是无 IO、无 Android 依赖的纯计算 —— 这样连续天数 / 月度表 / 热力图
 * 都能用 JVM 单测直接覆盖（不用起 Room，也不用设备）。
 *
 * ## 入参约定
 *
 * 所有 map 的 key 都是「毫秒时间戳」，但内部一律先归一到 [LocalDate] 再做日期运算，
 * 避免夏令时切换那天的 23 / 25 小时把日界算错（见 [HabitDates]）。
 */

/**
 * 目标达成判定。
 *
 * @param count 该周期内累计次数
 * @param targetCount 目标次数
 */
fun isTargetMet(count: Int, targetCount: Int): Boolean = count >= targetCount

/**
 * 连续达成天数（每日 N 次的目标）。
 *
 * ## 语义
 *
 * - **今天还没打卡不算断**：今天尚未达标时，从**昨天**开始往前数 ——
 *   今天还有机会，提前判「断了」会让用户晚上打卡前一直看着 0。
 * - 中间任意一天没达标 → 立刻归零（再往前也不续）。
 * - 补卡把中间某天补上后，连续天数会自然重新连起来。
 *
 * @param dayCounts 日期（当天 00:00 毫秒）→ 当天累计次数
 * @param today 今天 00:00 的毫秒时间戳
 * @param targetCount 每日目标次数
 */
fun computeDailyStreak(
    dayCounts: Map<Long, Int>,
    today: Long,
    targetCount: Int,
): Int {
    // 目标次数非法（<= 0）时永远「达成」，会让下面 while 死循环 —— 直接判 0 兜底
    if (targetCount <= 0) return 0
    val byDate = dayCounts.toLocalDateCounts()
    val todayDate = HabitDates.toLocalDate(today)
    var cursor = if (isTargetMet(byDate[todayDate] ?: 0, targetCount)) {
        todayDate
    } else {
        todayDate.minusDays(1)
    }
    var streak = 0
    while (isTargetMet(byDate[cursor] ?: 0, targetCount)) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/**
 * 连续达成周数（每周 N 次的目标）。
 *
 * 语义与 [computeDailyStreak] 一致：本周还没达标时从**上周**开始数。
 *
 * @param weekCounts 周一 00:00 毫秒 → 该周累计次数
 * @param currentWeekStart 本周周一 00:00 的毫秒时间戳
 * @param targetCount 每周目标次数
 */
fun computeWeeklyStreak(
    weekCounts: Map<Long, Int>,
    currentWeekStart: Long,
    targetCount: Int,
): Int {
    if (targetCount <= 0) return 0
    val byWeek = weekCounts.toLocalDateCounts()
    val thisWeek = HabitDates.toLocalDate(currentWeekStart)
    var cursor = if (isTargetMet(byWeek[thisWeek] ?: 0, targetCount)) {
        thisWeek
    } else {
        thisWeek.minusWeeks(1)
    }
    var streak = 0
    while (isTargetMet(byWeek[cursor] ?: 0, targetCount)) {
        streak++
        cursor = cursor.minusWeeks(1)
    }
    return streak
}

/**
 * 按周聚合：周一 00:00 → 该周累计次数。
 */
fun aggregateByWeek(dayCounts: Map<Long, Int>): Map<Long, Int> {
    val result = mutableMapOf<Long, Int>()
    dayCounts.forEach { (millis, count) ->
        val weekStart = HabitDates.startOfWeekMillis(HabitDates.toLocalDate(millis))
        result[weekStart] = (result[weekStart] ?: 0) + count
    }
    return result
}

/**
 * 月度打卡表：某年某月的**每一天**一格。
 *
 * 没打卡的日子也要占一格（count = 0）—— 月视图要的是完整日历，
 * 不是只列有记录的日子（那样用户看不出「哪天漏了」）。
 *
 * @param weekly true = 每周 N 次的目标（判定按「所在周累计」）；false = 每日 N 次（按当天）
 */
fun buildMonthGrid(
    year: Int,
    month: Int,
    dayCounts: Map<Long, Int>,
    targetCount: Int,
    weekly: Boolean = false,
): List<HabitDayCell> {
    val byDate = dayCounts.toLocalDateCounts()
    val weekTotals = aggregateByWeek(dayCounts).toLocalDateCounts()
    val first = LocalDate.of(year, month, 1)
    val days = first.lengthOfMonth()
    return (1..days).map { day ->
        val date = first.withDayOfMonth(day)
        val count = byDate[date] ?: 0
        HabitDayCell(
            date = HabitDates.startOfDayMillis(date),
            count = count,
            isMet = metFlag(date, count, weekTotals, targetCount, weekly),
        )
    }
}

/**
 * 年度热力图：按「周」切分，每格一天。
 *
 * 首尾会对齐到完整周（从 1 月 1 日所在周的周一开始，到 12 月 31 日所在周的周日结束），
 * 这样每一列的高度一致 —— 跟 GitHub 贡献图是同一套排布。
 */
fun buildYearHeatmap(
    year: Int,
    dayCounts: Map<Long, Int>,
    targetCount: Int,
    weekly: Boolean = false,
): List<HabitHeatmapWeek> {
    val byDate = dayCounts.toLocalDateCounts()
    val weekTotals = aggregateByWeek(dayCounts).toLocalDateCounts()
    val firstDay = LocalDate.of(year, 1, 1)
    val lastDay = LocalDate.of(year, 12, 31)
    var cursor = HabitDates.mondayOf(firstDay)
    val end = HabitDates.mondayOf(lastDay).plusDays(6)
    val weeks = mutableListOf<HabitHeatmapWeek>()
    while (!cursor.isAfter(end)) {
        val cells = (0..6).map { offset ->
            val date = cursor.plusDays(offset.toLong())
            val count = byDate[date] ?: 0
            val met = metFlag(date, count, weekTotals, targetCount, weekly)
            HabitHeatmapCell(
                date = HabitDates.startOfDayMillis(date),
                count = count,
                level = heatmapLevel(count, targetCount, met),
            )
        }
        weeks += HabitHeatmapWeek(
            weekStart = HabitDates.startOfDayMillis(cursor),
            cells = cells,
        )
        cursor = cursor.plusWeeks(1)
    }
    return weeks
}

/**
 * 热力图色阶：0 = 没打卡，4 = 达成目标，1~3 = 部分完成。
 */
fun heatmapLevel(count: Int, targetCount: Int, met: Boolean): Int {
    if (count <= 0) return 0
    if (met) return 4
    val target = targetCount.coerceAtLeast(1)
    return ((count * 3f) / target).toInt().coerceIn(1, 3)
}

/**
 * 把「习惯 + 打卡记录」组装成统计结果（纯函数，方便单测）。
 *
 * @param habit 习惯定义
 * @param checkIns 该习惯的打卡记录（时间范围内）
 * @param year 月度表 / 热力图显示的年份
 * @param month 月度表显示的月份（1..12）
 * @param today 今天 00:00 的毫秒时间戳
 */
fun buildHabitStats(
    habit: Habit,
    checkIns: List<HabitCheckIn>,
    year: Int,
    month: Int,
    today: Long,
): HabitStats {
    val dayCounts = checkIns.associate { it.date to it.count }
    val weekly = habit.targetType == HabitTargetType.WEEKLY
    val streak = if (weekly) {
        computeWeeklyStreak(
            weekCounts = aggregateByWeek(dayCounts),
            currentWeekStart = HabitDates.startOfWeekMillis(HabitDates.toLocalDate(today)),
            targetCount = habit.targetCount,
        )
    } else {
        computeDailyStreak(dayCounts, today, habit.targetCount)
    }
    val todayCount = dayCounts[today] ?: 0
    return HabitStats(
        habit = habit,
        streak = streak,
        streakUnit = if (weekly) StreakUnit.WEEK else StreakUnit.DAY,
        todayCount = todayCount,
        todayMet = isTargetMet(todayCount, habit.targetCount),
        monthCells = buildMonthGrid(year, month, dayCounts, habit.targetCount, weekly),
        heatmapWeeks = buildYearHeatmap(year, dayCounts, habit.targetCount, weekly),
    )
}

/**
 * 某一天是否达标。
 *
 * 每周目标时看**所在周的累计**（一周里任何一天凑够就算这周达成，
 * 于是那一周的所有格子都会亮 —— 和「这周还差 2 次」的心智一致）。
 */
private fun metFlag(
    date: LocalDate,
    count: Int,
    weekTotals: Map<LocalDate, Int>,
    targetCount: Int,
    weekly: Boolean,
): Boolean = if (weekly) {
    isTargetMet(weekTotals[HabitDates.mondayOf(date)] ?: 0, targetCount)
} else {
    isTargetMet(count, targetCount)
}

/** 把「毫秒 → 次数」的映射按本地日期归一 */
private fun Map<Long, Int>.toLocalDateCounts(): Map<LocalDate, Int> =
    entries.associate { HabitDates.toLocalDate(it.key) to it.value }
