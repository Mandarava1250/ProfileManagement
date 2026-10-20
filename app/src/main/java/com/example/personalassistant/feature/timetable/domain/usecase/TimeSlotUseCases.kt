package com.example.personalassistant.feature.timetable.domain.usecase

import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule
import com.example.personalassistant.feature.timetable.domain.repository.TimeSlotRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 观察当前课时表 */
class ObserveTimeSlotScheduleUseCase @Inject constructor(
    private val repository: TimeSlotRepository,
) {
    operator fun invoke(): Flow<TimeSlotSchedule> = repository.observeSchedule()
}

/** 保存整张课时表（替换语义） */
class SaveTimeSlotScheduleUseCase @Inject constructor(
    private val repository: TimeSlotRepository,
) {
    suspend operator fun invoke(schedule: TimeSlotSchedule) {
        repository.saveSchedule(schedule)
    }
}

/** 重置为内置默认 12 节课时表 */
class ResetTimeSlotScheduleUseCase @Inject constructor(
    private val repository: TimeSlotRepository,
) {
    suspend operator fun invoke() {
        repository.resetToDefault()
    }
}
