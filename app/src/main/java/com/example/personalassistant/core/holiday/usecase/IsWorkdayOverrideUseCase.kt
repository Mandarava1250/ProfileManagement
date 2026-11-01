package com.example.personalassistant.core.holiday.usecase

import com.example.personalassistant.core.holiday.data.HolidayRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Phase 8 Sub-step 7：判断指定日期是否为调休工作日（WORKDAY）。
 *
 * 用于「明天是调休工作日」的提醒文案生成（参见 HolidayEveningReminderWorker）。
 *
 * 例：2025-09-28（周日）标记为 WORKDAY，因为国庆前补班。
 */
class IsWorkdayOverrideUseCase @Inject constructor(
    private val holidayRepository: HolidayRepository,
) {
    suspend operator fun invoke(date: LocalDate): Boolean =
        holidayRepository.findWorkdayOverride(date) != null
}