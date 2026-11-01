package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import javax.inject.Inject

/**
 * Phase 7c Sub-step 5：删除事件
 *
 * ## 职责
 * - 业务校验：id > 0（不能传入 0，避免误删「未保存」状态）
 * - repository 对不存在的 id 静默 no-op（幂等）
 *
 * ## 接受 Event 还是 id
 * - 这里只接 id（跟 DeleteTaskUseCase 接 Task 的风格略不同）
 * - 理由：删除按钮触发的瞬间已有完整 Event，但 Event 可能存在 id=0 的「草稿」状态，
 *   强校验 id > 0 能保护草稿不被错误清除
 *
 * 设计取舍：保留 deletedAt 软删除字段留到 Phase 8，本期直接物理删除。
 */
open class DeleteEventUseCase @Inject constructor(
    private val repository: CalendarRepository,
) {
    open suspend operator fun invoke(id: Long) {
        require(id > 0L) { "删除事件的 id 必须大于 0" }
        repository.deleteEvent(id)
    }
}
