package com.example.personalassistant.feature.calendar.domain.model

import com.example.personalassistant.feature.calendar.domain.util.EventTimeMapper
import com.example.personalassistant.feature.timetable.domain.model.Course

/**
 * Phase 20.5：日历里的一条冲突记录（清单项 ↔ 课程）
 *
 * ## 为什么单独一个模型
 *
 * 事件与课程的冲突用的是 [EventConflictInfo]（Phase 8），
 * 那是「事件编辑时」的模型 —— 事件有 id、有 startTime/endTime。
 *
 * 清单项的冲突是「今天这个时间段，跟哪几门课撞上了」，
 * 主体是清单项而不是事件，所以单独建模。
 *
 * ## 怎么用
 *
 * UI 层把它转成 [EventConflictInfo] 交给既有的
 * [com.example.personalassistant.feature.calendar.presentation.components.ConflictWarningBanner] 渲染 ——
 * 横幅本身不用改。
 *
 * @property taskId 冲突的清单项 id
 * @property taskTitle 清单项标题
 * @property conflictingCourses 撞上的课程
 * @property weekNumber 当前学期内的周次（null = 不在学期内）
 * @property timeSlice 清单项在当天的时段切片（供横幅复用）
 */
data class CalendarConflictEntry(
    val taskId: Long,
    val taskTitle: String,
    val conflictingCourses: List<Course>,
    val weekNumber: Int? = null,
    val timeSlice: EventTimeMapper.TimeSlice? = null,
) {
    /** 是否有冲突（没有冲突的条目不会出现在列表里，这里只是方便调用方判断） */
    val hasConflict: Boolean get() = conflictingCourses.isNotEmpty()

    /**
     * 转成既有的 [EventConflictInfo] —— 直接喂给 ConflictWarningBanner
     */
    fun toEventConflictInfo(): EventConflictInfo = EventConflictInfo(
        conflictingCourses = conflictingCourses,
        weekNumber = weekNumber,
        timeSlice = timeSlice,
    )
}
