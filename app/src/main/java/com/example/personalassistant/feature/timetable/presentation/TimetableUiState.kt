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