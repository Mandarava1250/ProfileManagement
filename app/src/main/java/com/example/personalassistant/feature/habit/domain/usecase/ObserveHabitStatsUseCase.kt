package com.example.personalassistant.feature.habit.domain.usecase

import com.example.personalassistant.feature.habit.domain.model.HabitStats
import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import com.example.personalassistant.feature.habit.domain.util.HabitDates
import com.example.personalassistant.feature.habit.domain.util.buildHabitStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Phase 23：观察单个习惯的统计（连续天数 / 月度打卡表 / 年度热力图）。
 *
 * @param habitId 习惯 id
 * @param year 热力图与月度表显示的年份
 * @param month 月度表显示的月份（1..12）
 */
class ObserveHabitStatsUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    operator fun invoke(habitId: Long, year: Int, month: Int): Flow<HabitStats?> {
        require(habitId > 0L) { "习惯 id 无效" }
        val today = HabitDates.todayStartMillis()
        val yearStart = HabitDates.startOfYearMillis(year)
        val yearEnd = HabitDates.endOfYearMillis(year)
        // 连续天数可能从去年就开始了 —— 往前多拉 400 天，
        // 否则 1 月份查历史记录时 streak 会在年初被硬生生截断。
        val streakFloor = HabitDates.startOfDayMillis(
            HabitDates.toLocalDate(today).minusDays(400),
        )
        val rangeStart = minOf(yearStart, streakFloor)
        val rangeEnd = maxOf(yearEnd, today)
        return combine(
            repository.observeHabitById(habitId),
            repository.observeCheckIns(habitId, rangeStart, rangeEnd),
        ) { habit, checkIns ->
            habit?.let { buildHabitStats(it, checkIns, year, month, today) }
        }
    }
}
