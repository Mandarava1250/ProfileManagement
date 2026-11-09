package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import javax.inject.Inject

/**
 * Phase 20.1：给清单项设置 / 清除时间段
 *
 * ## 语义
 *
 * - `startAt` / `endAt` **都有值** = 该清单项在日历里是一个时段块
 * - 两个都传 null = 清除时间段（它回到「只有截止时间」的普通清单项）
 * - 任务 id 不存在 → 静默 no-op（跟其它日历写操作一致）
 *
 * ## 为什么走 CalendarRepository
 *
 * 日历模块本来就直接持有 `TaskDao`（联动聚合需要读清单项），
 * 所以「写回去」也放在同一处，避免同一个表有两个写入口。
 */
class SetTaskTimeRangeUseCase @Inject constructor(
    private val repository: CalendarRepository,
) {
    suspend operator fun invoke(taskId: Long, startAt: Long?, endAt: Long?) {
        if (taskId <= 0L) return
        // 只有一列有值 = 没填完整，当作没填
        val hasRange = startAt != null && endAt != null
        repository.setTaskTimeRange(
            taskId = taskId,
            startAt = if (hasRange) startAt else null,
            endAt = if (hasRange) endAt else null,
        )
    }
}
