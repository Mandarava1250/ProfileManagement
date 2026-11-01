package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 7c：观察单日详情（事件 + 任务 + 课表聚合）
 *
 * 涟宝解读：
 * - UI 层（DayDetailSheet）直接 collect 这个 Flow
 * - 任何源数据变更（事件增删、任务完成、课表编辑）会自动重发新的 DayDetail
 *
 * @Inject constructor 由 Hilt 自动构造，无需 @Provides
 */
class ObserveDayDetailUseCase @Inject constructor(
    private val repository: CalendarRepository,
) {
    operator fun invoke(date: Long): Flow<DayDetail> =
        repository.observeDayDetail(date)
}