package com.example.personalassistant.feature.habit.domain.usecase

import com.example.personalassistant.feature.habit.domain.model.HabitTodayItem
import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import com.example.personalassistant.feature.habit.domain.util.HabitDates
import com.example.personalassistant.feature.habit.domain.util.isTargetMet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Phase 23：观察「今日习惯」列表（习惯 + 今天已打卡次数）。
 *
 * ⚠️ 「今天」在 UseCase 构造流的时候取一次。App 跨零点不重启的话，
 * 列表会一直按「打开时的那天」算 —— 这是刻意的：跨零点自动刷新会让
 * 用户正在看的一屏数据突然跳变，体验比「零点后要手动退出重进」更差。
 */
class ObserveTodayHabitsUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    operator fun invoke(): Flow<List<HabitTodayItem>> {
        val today = HabitDates.todayStartMillis()
        return combine(
            repository.observeHabits(includeArchived = false),
            repository.observeCheckInsOn(today),
        ) { habits, checkIns ->
            val countByHabit = checkIns.associate { it.habitId to it.count }
            habits.map { habit ->
                val count = countByHabit[habit.id] ?: 0
                HabitTodayItem(
                    habit = habit,
                    count = count,
                    isMet = isTargetMet(count, habit.targetCount),
                )
            }
        }
    }
}
