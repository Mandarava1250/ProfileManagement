package com.example.personalassistant.feature.timetable.presentation.edit

import com.example.personalassistant.feature.timetable.domain.model.Course

/**
 * 课程编辑页 UI 状态
 *
 * 字段含义：所有字段都对应表单输入，UI 直接绑定即可。
 * isEdit 区分新建/编辑；isSaving 防止重复提交。
 */
data class CourseEditUiState(
    val isLoading: Boolean = false,
    val isEdit: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,

    // 表单字段
    val id: Long = 0L,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int = 1,            // 1..7
    val startMinutes: Int = 8 * 60,    // 默认 08:00
    val endMinutes: Int = 9 * 60 + 45, // 默认 09:45
    val colorIndex: Int = 0,           // 调色板索引
    val semester: String = "",
    val weeks: String = "",            // 空表示全学期
    val reminderMinutes: Int = 15,
    val note: String = "",

    val availableSemesters: List<String> = emptyList(),
) {
    val isValid: Boolean
        get() = name.isNotBlank() && endMinutes > startMinutes

    fun toCourse(defaultColor: Int): Course = Course(
        id = id,
        name = name.trim(),
        teacher = teacher.trim(),
        location = location.trim(),
        dayOfWeek = dayOfWeek,
        startTime = startMinutes,
        endTime = endMinutes,
        color = defaultColor,
        semester = semester.ifBlank { null },
        weeks = weeks.trim().ifBlank { null },
        reminderMinutes = reminderMinutes,
        note = note.trim().ifBlank { null },
    )
}