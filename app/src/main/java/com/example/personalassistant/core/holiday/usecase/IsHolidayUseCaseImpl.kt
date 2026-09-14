package com.example.personalassistant.core.holiday.usecase

import com.example.personalassistant.core.holiday.data.HolidayRepository
import com.example.personalassistant.core.holiday.model.HolidayType
import java.time.LocalDate
import javax.inject.Inject

/**
 * Phase 8 Sub-step 7：[IsHolidayUseCase] 的生产实现。
 *
 * 由 Hilt 自动发现并注入（@Inject + Singleton）。
 */
class IsHolidayUseCaseImpl @Inject constructor(
    private val holidayRepository: HolidayRepository,
) : IsHolidayUseCase {
    override suspend fun invoke(date: LocalDate): Boolean =
        holidayRepository.getHolidaysByDate(date).any { it.type == HolidayType.HOLIDAY }
}