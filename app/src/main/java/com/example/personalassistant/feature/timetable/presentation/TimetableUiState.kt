package com.example.personalassistant.feature.timetable.presentation

import com.example.personalassistant.feature.timetable.domain.model.CourseConflict
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule

/**
 * 课表 UI 状态
 *
 * 包含：
 *   - 当前选中的学期（userVisibleNullValue = 未设置）
 *   - 当前选中的周次（1-based）
 *   - 本周要画的**课时块**列表（[CourseSlot] = 一门课的一个课时）
 *   - 学期列表（供 SemesterSelector）
 *   - 课时表（决定 grid 行高 + 编辑对话框）
 *   - 一次性事件（冲突提示、错误消息、删除确认等）
 */
data class TimetableUiState(
    val semesters: List<Semester> = emptyList(),
    val currentSemester: Semester? = null,
    val currentWeekNumber: Int = 1,
    /**
     * 当前学期的总周数（0 = 没有学期 / 学期信息缺失）。
     *
     * 翻周的时候用它把周数钳在 `[0, totalWeeks]` —— 往前翻不会翻出负数，
     * 往后翻也不会翻到学期外面去。
     */
    val totalWeeks: Int = 0,
    /**
     * 当前是否停在「本周」。
     *
     * 手动翻到别的周之后才为 false —— 「回到本周」按钮也就只有这时才该出现。
     * 之前这个值是写死的 `true`，按钮永远不显示，用户手动翻周之后回不去。
     */
    val isCurrentWeek: Boolean = true,
    /**
     * 本周要画的所有**课时块**（Phase 28：一个课时一块）
     *
     * 一门课有三个课时 → 三个 [CourseSlot]（course 相同、session 不同）。
     * 排序：先按星期的 [CourseSlot.session] 的星期几，再按开始时间。
     */
    val slots: List<CourseSlot> = emptyList(),
    val timeSlotSchedule: TimeSlotSchedule = TimeSlotSchedule.default(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    /** 一次性事件 */
    /**
     * 待确认的课程冲突（Phase 28：跨课程冲突 + 同课程内部课时重叠）
     *
     * 存整个 [CourseConflict] 而不是只存 `newCourse` ——
     * 同门课两个课时互相重叠这类冲突**只存在于** [CourseConflict.internalOverlaps] 里，
     * 只留 `newCourse` 的话这层信息在 UI 上就丢了。
     */
    val pendingConflict: CourseConflict? = null,
    val showDeleteConfirmId: Long? = null,
) {
    /** 当前选中学期下的「在当前周上课」的**节数**（一个课时算一节） */
    val activeSlotCount: Int get() = slots.size

    /** 当前选中学期下的**门数**（同一门课的多个课时只算一门） */
    val activeCourseCount: Int get() = slots.map { it.course.id }.distinct().size

    /** 是否存在当前学期 */
    val hasCurrentSemester: Boolean get() = currentSemester != null
}
