package com.example.personalassistant.core.holiday.usecase

import com.example.personalassistant.core.holiday.data.HolidayRepository
import com.example.personalassistant.core.holiday.model.HolidayEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

/**
 * Phase 8 Sub-step 7：观察未来 N 天的节假日列表。
 *
 * 用途：HolidayEveningReminderWorker / Settings 卡片「未来节假日预览」/ 首页装饰。
 *
 * @param days 未来天数（默认 30 天）
 */
class ObserveUpcomingHolidaysUseCase @Inject constructor(
    private val holidayRepository: HolidayRepository,
) {
    operator fun invoke(
        from: LocalDate = LocalDate.now(),
        days: Int = 30,
    ): Flow<List<HolidayEntity>> {
        val to = from.plusDays(days.toLong())
        return holidayRepository.observeHolidaysInRange(from, to)
    }
}