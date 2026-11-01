package com.example.personalassistant.feature.timetable.presentation.edit

import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule

/**
 * 课程编辑页 UI 状态
 *
 * 字段含义：所有字段都对应表单输入，UI 直接绑定即可。
 * isEdit 区分新建/编辑；isSaving 防止重复提交。
 *
 * 时间模式：用户可在"时段模式（HH:mm-HH:mm）"和"课时模式（选第 N 节）"之间切换。
 *  - [mode] = CLOCK → [startMinutes]/[endMinutes] 生效
 *  - [mode] = PERIOD → [startPeriodIndex]/[endPeriodIndex] 生效，
 *    真正落库前 ViewModel 会按 [availableTimeSlots] 把 index 翻译回 minutes
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

    // 时间模式
    val mode: TimeMode = TimeMode.CLOCK,
    val startMinutes: Int = 8 * 60,    // 时段模式：08:00
    val endMinutes: Int = 9 * 60 + 45, // 时段模式：09:45
    val startPeriodIndex: Int = 1,     // 课时模式：第 1 节
    val endPeriodIndex: Int = 2,       // 课时模式：第 2 节

    val availableTimeSlots: TimeSlotSchedule = TimeSlotSchedule.default(),

    val colorIndex: Int = 0,
    val semester: String = "",
    val weeks: String = "",            // 空表示全学期
    val reminderMinutes: Int = 15,
    val note: String = "",

    /** 学分（Phase 12 新增；空字符串 = 未填，落库为 null） */
    val creditText: String = "",

    /** 绩点 0.0-4.0（Phase 12 新增；空字符串 = 未填，落库为 null） */
    val gradePointText: String = "",

    val availableSemesters: List<String> = emptyList(),
) {
    /**
     * 根据当前模式，把"在 UI 里展示的时段"折算成"要落库的分钟区间"。
     *
     *  - CLOCK：原样返回 startMinutes/endMinutes
     *  - PERIOD：按 [availableTimeSlots] 查 start/end index 对应的起止 minutes
     *
     * 这样保存到 Course 实体时，无须关心模式，只看 startMinutes/endMinutes 即可。
     */
    fun effectiveMinutes(): Pair<Int, Int> = when (mode) {
        TimeMode.CLOCK -> startMinutes to endMinutes
        TimeMode.PERIOD -> {
            val s = availableTimeSlots.periods.firstOrNull { it.index == startPeriodIndex }?.startMinutes
                ?: startMinutes
            val e = availableTimeSlots.periods.firstOrNull { it.index == endPeriodIndex }?.endMinutes
                ?: endMinutes
            s to e
        }
    }

    val isValid: Boolean
        get() {
            if (name.isBlank()) return false
            // 学期：编辑模式可保留空；新建模式必须选一个
            if (!isEdit && semester.isBlank()) return false
            val (s, e) = effectiveMinutes()
            return e > s
        }

    fun toCourse(defaultColor: Int): Course {
        val (s, e) = effectiveMinutes()
        return Course(
            id = id,
            name = name.trim(),
            teacher = teacher.trim(),
            location = location.trim(),
            dayOfWeek = dayOfWeek,
            startTime = s,
            endTime = e,
            color = defaultColor,
            semester = semester.ifBlank { null },
            weeks = weeks.trim().ifBlank { null },
            reminderMinutes = reminderMinutes,
            note = note.trim().ifBlank { null },
            // 空串 → null，非法输入（非数字）→ null（不让用户因手滑写坏 DB）
            credit = creditText.trim().toDoubleOrNull()?.takeIf { it > 0.0 },
            gradePoint = gradePointText.trim().toDoubleOrNull()?.takeIf { it in 0.0..4.0 },
        )
    }
}

/** 课程编辑页的时间模式 */
enum class TimeMode { CLOCK, PERIOD }
