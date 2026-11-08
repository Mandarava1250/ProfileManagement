package com.example.personalassistant.feature.timetable.presentation

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule

/**
 * 课表 UI 状态
 *
 * 包含：
 *   - 当前选中的学期（userVisibleNullValue = 未设置）
 *   - 当前选中的周次（1-based）
 *   - 课程列表（按 dayOfWeek + startTime 排序）
 *   - 学期列表（供 SemesterSelector）
 *   - 课时表（决定 grid 行高 + 编辑对话框）
 *   - 一次性事件（冲突提示、错误消息、删除确认等）
 */
data class TimetableUiState(
    val semesters: List<Semester> = emptyList(),
    val currentSemester: Semester? = null,
    val currentWeekNumber: Int = 1,
    /**
     * 当前是否停在「本周」。
     *
     * 手动翻到别的周之后才为 false —— 「回到本周」按钮也就只有这时才该出现。
     * 之前这个值是写死的 `true`，按钮永远不显示，用户手动翻周之后回不去。
     */
    val isCurrentWeek: Boolean = true,
    val courses: List<Course> = emptyList(),
    val timeSlotSchedule: TimeSlotSchedule = TimeSlotSchedule.default(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    /** 一次性事件 */
    val pendingConflict: Course? = null,
    val showDeleteConfirmId: Long? = null,
) {
    /** 当前选中学期下的"在当前周上课"的有效课程数 */
    val activeCoursesCount: Int get() = courses.size

    /** 是否存在当前学期 */
    val hasCurrentSemester: Boolean get() = currentSemester != null
}