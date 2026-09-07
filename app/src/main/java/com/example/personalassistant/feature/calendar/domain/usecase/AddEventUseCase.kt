package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import javax.inject.Inject

/**
 * Phase 7c Sub-step 5：新增事件
 *
 * ## 职责
 * - 业务校验：title 非空、endTime >= startTime（如同时给定）
 * - id = 0 表示新增，repository 自动生成 rowId
 * - 返回落库后的 id
 *
 * ## 与 UpdateEventUseCase 的区别
 * - Add：id 必须为 0；repository 会 INSERT（生成新 id）
 * - Update：id > 0；repository 会 UPDATE 已有记录
 *
 * 设计取舍：不做颜色/分类默认值处理（UI 层负责给合理初值）。
 */
open class AddEventUseCase @Inject constructor(
    private val repository: CalendarRepository,
) {
    open suspend operator fun invoke(event: Event): Long {
        require(event.id == 0L) { "新增事件的 id 必须为 0（已存在事件请用 UpdateEventUseCase）" }
        require(event.title.isNotBlank()) { "事件标题不能为空" }
        validateTimeRange(event)
        return repository.addEvent(event)
    }

    private fun validateTimeRange(event: Event) {
        val start = event.startTime
        val end = event.endTime
        if (start != null && end != null) {
            require(end >= start) { "结束时间不能早于开始时间" }
        }
    }
}
