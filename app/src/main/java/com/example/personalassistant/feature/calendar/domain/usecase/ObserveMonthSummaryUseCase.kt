package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.MonthSummary
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 7c：观察月范围每日汇总（轻量 badge 数据）
 *
 * 涟宝解读：
 * - 输入：monthStart + monthEnd + today 三个时间戳
 * - 输出：Map<当天 00:00 时间戳, DaySummary>
 * - UI 层（MonthCalendarGrid）按 date 索引 O(1) 取 badge 数据
 * - monthStart, monthEnd, today 应由 ViewModel 一次性算出传入（保证 today 不会被多次调用时漂移）
 *
 * @Inject constructor 由 Hilt 自动构造，无需 @Provides
 */
class ObserveMonthSummaryUseCase @Inject constructor(
    private val repository: CalendarRepository,
) {
    operator fun invoke(monthStart: Long, monthEnd: Long, today: Long): Flow<MonthSummary> =
        repository.observeMonthSummary(monthStart, monthEnd, today)
}