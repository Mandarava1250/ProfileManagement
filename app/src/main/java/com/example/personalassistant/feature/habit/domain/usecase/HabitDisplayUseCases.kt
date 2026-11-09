package com.example.personalassistant.feature.habit.domain.usecase

import com.example.personalassistant.feature.habit.domain.repository.HabitDisplayRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 23.6：观察「习惯在日历同屏显示」开关。
 */
class ObserveShowHabitInCalendarUseCase @Inject constructor(
    private val repository: HabitDisplayRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeShowInCalendar()
}

/**
 * Phase 23.6：设置「习惯在日历同屏显示」开关。
 */
class SetShowHabitInCalendarUseCase @Inject constructor(
    private val repository: HabitDisplayRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setShowInCalendar(enabled)
    }
}
