package com.example.personalassistant.feature.calendar.domain.model

import com.example.personalassistant.feature.calendar.domain.util.EventTimeMapper
import com.example.personalassistant.feature.timetable.domain.model.Course

/**
 * Phase 8 Sub-step 9 Step 4：单个 Event 的冲突检测结果
 *
 * ## 三种合法状态（由调用方根据需求选用）
 *
 * | 场景 | conflictingCourses | weekNumber | timeSlice |
 * |---|---|---|---|
 * | 整天事件 | emptyList() | null（无关） | null |
 * | 学期外（早于 startDate / 晚于 endDate） | emptyList() | null | TimeSlice |
 * | 学期内 + 精确时段 | 可能非空 | 1..N | TimeSlice |
 *
 * 设计取舍：
 * - 不区分"无冲突是因为整天 / 学期外 / 真无冲突"——三者都返回 hasConflict=false
 * - 调用方按 [isAllDay] 自行决定 UI 渲染策略
 */
data class EventConflictInfo(
    val conflictingCourses: List<Course>,
    val weekNumber: Int?,
    val timeSlice: EventTimeMapper.TimeSlice?,
) {
    /** 是否存在与课程的时段冲突 */
    val hasConflict: Boolean get() = conflictingCourses.isNotEmpty()

    /** 是否为整天事件（timeSlice == null） */
    val isAllDay: Boolean get() = timeSlice == null

    /** 是否学期内（weekNumber != null） */
    val isWithinSemester: Boolean get() = weekNumber != null
}