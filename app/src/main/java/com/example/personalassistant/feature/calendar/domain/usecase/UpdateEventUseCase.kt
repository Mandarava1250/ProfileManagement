package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import javax.inject.Inject

/**
 * Phase 7c Sub-step 5：更新事件
 *
 * ## 职责
 * - 业务校验：id > 0（更新已存在的事件）、title 非空、endTime >= startTime
 * - 自动刷新 updatedAt 时间戳（VM 层可以传新值，repository 也会再刷新一次）
 *
 * ## 幂等性
 * - repository.updateEvent 对不存在的 id 静默 no-op
 * - UseCase 这里不抛异常（保持幂等），调用方根据 repo 反馈自行处理
 *
 * 设计取舍：保留原 createdAt，不在 UseCase 层修改时间戳（由 repository/Mapper 负责）。
 */
open class UpdateEventUseCase @Inject constructor(
    private val repository: CalendarRepository,
) {
    open suspend operator fun invoke(event: Event) {
        require(event.id > 0L) { "更新事件的 id 必须大于 0" }
        require(event.title.isNotBlank()) { "事件标题不能为空" }
        validateTimeRange(event)
        repository.updateEvent(event)
    }

    private fun validateTimeRange(event: Event) {
        val start = event.startTime
        val end = event.endTime
        if (start != null && end != null) {
            require(end >= start) { "结束时间不能早于开始时间" }
        }
    }
}
