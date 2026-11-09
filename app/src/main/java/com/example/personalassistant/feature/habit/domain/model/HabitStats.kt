package com.example.personalassistant.feature.habit.domain.model

/**
 * Phase 23：习惯统计（聚合输出）
 *
 * - [streak]：连续达成的周期数（天 / 周，见 [streakUnit]）
 * - [monthCells]：月度打卡表（每一天一格）
 * - [heatmapWeeks]：年度热力图（按周切分，每格一天）
 */
data class HabitStats(
    val habit: Habit,
    val streak: Int,
    val streakUnit: StreakUnit,
    /** 今天已累计次数 */
    val todayCount: Int,
    /** 今天是否达成目标 */
    val todayMet: Boolean,
    val monthCells: List<HabitDayCell>,
    val heatmapWeeks: List<HabitHeatmapWeek>,
) {
    /** 连续达成的展示文案（如「连续 5 天」「连续 2 周」） */
    val streakText: String
        get() = when (streakUnit) {
            StreakUnit.DAY -> "连续 $streak 天"
            StreakUnit.WEEK -> "连续 $streak 周"
        }
}

/** 连续达成的计数单位 */
enum class StreakUnit {
    /** 每日 N 次目标 → 单位是天 */
    DAY,

    /** 每周 N 次目标 → 单位是周 */
    WEEK,
}

/**
 * 月度打卡表的一格
 *
 * @param date 当天 00:00 的毫秒时间戳
 * @param count 当天累计次数
 * @param isMet 当天（每周目标时是「所在周」）是否达成目标
 */
data class HabitDayCell(
    val date: Long,
    val count: Int,
    val isMet: Boolean,
)

/**
 * 年度热力图的一格
 *
 * @param level 色阶：0 = 没打卡，1~3 = 部分完成，4 = 达成目标
 */
data class HabitHeatmapCell(
    val date: Long,
    val count: Int,
    val level: Int,
)

/**
 * 年度热力图的一周（周一起算，固定 7 格）
 *
 * @param weekStart 本周周一 00:00 的毫秒时间戳
 */
data class HabitHeatmapWeek(
    val weekStart: Long,
    val cells: List<HabitHeatmapCell>,
)
