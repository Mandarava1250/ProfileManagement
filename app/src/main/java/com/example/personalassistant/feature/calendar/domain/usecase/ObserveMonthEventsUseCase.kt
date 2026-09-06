package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 7c：观察月范围事件流
 *
 * 用途：月视图（6×7 网格）的数据源。一次拉一个完整月的事件，
 * VM 在客户端按 date 分组成 Map<date, List<Event>> 喂给 [CalendarDayCell]。
 *
 * 涟宝解读：
 * - 入参是「月视图对应月的 [monthStart, monthEnd) 区间」
 * - monthStart = 当月第一天 00:00，monthEnd = 下月第一天 00:00
 * - 返回的 Event 列表已按 date ASC, startTime ASC 排序（DAO 层保证）
 *
 * @Inject constructor 由 Hilt 自动构造，无需 @Provides
 */
class ObserveMonthEventsUseCase @Inject constructor(
    private val repository: CalendarRepository,
) {
    operator fun invoke(monthStart: Long, monthEnd: Long): Flow<List<Event>> =
        repository.observeEventsByMonthRange(monthStart, monthEnd)
}