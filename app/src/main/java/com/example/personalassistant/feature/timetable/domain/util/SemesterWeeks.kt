package com.example.personalassistant.feature.timetable.domain.util

import com.example.personalassistant.feature.timetable.domain.model.Semester

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * 一个学期的总周数。
 *
 * 学期是「起止日期」而不是「几周」，所以周数要自己算：
 * 学期跨了多少天 ÷ 7 + 1（首周算一周）。
 *
 * 例：09-01 ~ 12-31 共 121 天 → 121 / 7 + 1 = 18 周。
 */
fun semesterTotalWeeks(semester: Semester): Int {
    val days = ((semester.endDate - semester.startDate) / MILLIS_PER_DAY)
        .toInt()
        .coerceAtLeast(0)
    return days / 7 + 1
}

/**
 * 把周数钳制到 `[0, totalWeeks]`。
 *
 * ## 为什么要钳
 *
 * 翻周的入口是「第 N 周 - 1」，用户在第 1 周继续往前翻就会得到 0，再翻就是 -1。
 * 负周号会让 `Course.appliesToWeek(-1)` 这种判断全部落空 —— 界面看着正常，
 * 课程却一门都不显示，极难排查。
 *
 * 上界同理：学期只有 18 周，翻到第 19 周以后也没有任何课程。
 *
 * @param totalWeeks 学期总周数；0（没有学期 / 学期信息缺失）时钳到 0
 */
fun clampWeekNumber(week: Int, totalWeeks: Int): Int =
    week.coerceIn(0, totalWeeks.coerceAtLeast(0))
