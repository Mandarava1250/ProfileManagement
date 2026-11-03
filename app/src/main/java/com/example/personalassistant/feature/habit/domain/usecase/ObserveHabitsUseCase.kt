package com.example.personalassistant.feature.habit.domain.usecase

import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 23：观察习惯列表。
 *
 * @param includeArchived true = 含归档；false = 只看未归档
 */
class ObserveHabitsUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    operator fun invoke(includeArchived: Boolean = false): Flow<List<Habit>> =
        repository.observeHabits(includeArchived)
}
