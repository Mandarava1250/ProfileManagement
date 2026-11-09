package com.example.personalassistant.feature.habit.domain.usecase

import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Phase 23：删除习惯（其打卡记录由 FK CASCADE 一并清掉）。
 */
class DeleteHabitUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    suspend operator fun invoke(habit: Habit) {
        repository.deleteHabit(habit)
    }
}
