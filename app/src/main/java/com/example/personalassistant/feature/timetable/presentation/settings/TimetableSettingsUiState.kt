package com.example.personalassistant.feature.timetable.presentation.settings

import com.example.personalassistant.feature.timetable.domain.model.Period
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule

/**
 * 课表设置页 UI 状态
 *
 * 学期编辑 + 课时表编辑共用此 UiState。
 * 编辑态是"草稿"：用户修改 [editingSemesters] / [editingSchedule] 但还没保存。
 * 保存时才通过 [SemesterCreateUseCase] / [SaveTimeSlotScheduleUseCase] 落库。
 */
data class TimetableSettingsUiState(
    /** 数据库里所有学期（只读） */
    val semesters: List<Semester> = emptyList(),
    /** 当前学期（isCurrent=true） */
    val currentSemester: Semester? = null,
    /** 用户正在编辑的草稿——学期（用于删除 / 切换 current） */
    val editingSemesters: List<Semester> = emptyList(),
    /** 课时表草稿（逐行可编辑） */
    val editingSchedule: TimeSlotSchedule = TimeSlotSchedule.default(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * 课时表编辑的"单行可编辑项" — UI 列表用
 *
 * 为什么不直接复用 [Period]？因为编辑态需要 [isDirty] / [hasError] 等 UI 标记，
 * 这些放在 domain 模型里会污染数据契约。
 */
data class PeriodEditRow(
    val index: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    /** UI 字段级错误；非空表示这一行不合法（如 end <= start） */
    val errorMessage: String? = null,
) {
    val startMinutes: Int get() = startHour * 60 + startMinute
    val endMinutes: Int get() = endHour * 60 + endMinute

    fun toDomain(): Period = Period(index = index, startMinutes = startMinutes, endMinutes = endMinutes)

    companion object {
        /** 从 [Period] 转 [PeriodEditRow]，无错误 */
        fun from(period: Period): PeriodEditRow = PeriodEditRow(
            index = period.index,
            startHour = period.startMinutes / 60,
            startMinute = period.startMinutes % 60,
            endHour = period.endMinutes / 60,
            endMinute = period.endMinutes % 60,
        )
    }
}
