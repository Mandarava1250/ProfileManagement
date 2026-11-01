package com.example.personalassistant.feature.timetable.presentation.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.ui.theme.CoursePalette
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveTimeSlotScheduleUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.UpsertCourseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 课程编辑页 ViewModel
 *
 *  - id = -1L：新建
 *  - id > 0：编辑，加载已有数据
 *
 * 模式说明：
 *  - 时间模式在 [TimeMode.CLOCK] / [TimeMode.PERIOD] 间切换
 *  - PERIOD 模式用 [availableTimeSlots] 把"第 N 节"翻成分钟区间
 */
@HiltViewModel
class CourseEditViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val upsertCourseUseCase: UpsertCourseUseCase,
    private val observeTimeSlotSchedule: ObserveTimeSlotScheduleUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseEditUiState())
    val uiState: StateFlow<CourseEditUiState> = _uiState.asStateFlow()

    /** 完成回调：true 表示保存成功，UI 层据此导航返回 */
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** 删除完成回调 */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        // 持续观察 TimeSlotSchedule（用户在设置页改了课时表，编辑页的下拉也得刷新）
        viewModelScope.launch {
            observeTimeSlotSchedule().collect { schedule ->
                _uiState.update { it.copy(availableTimeSlots = schedule) }
            }
        }
    }

    fun initialize(courseId: Long) {
        viewModelScope.launch {
            val semesters = repository.observeAllSemesters().first().map { it.name }
            val currentSem = repository.observeCurrentSemester().first()?.name
            val timeSlots = observeTimeSlotSchedule().first()

            if (courseId <= 0L) {
                // 新建：默认用当前学期 + 时段模式 + 第一个颜色
                _uiState.update {
                    it.copy(
                        isEdit = false,
                        availableSemesters = semesters,
                        semester = currentSem ?: semesters.firstOrNull().orEmpty(),
                        availableTimeSlots = timeSlots,
                        startPeriodIndex = 1,
                        endPeriodIndex = timeSlots.periods.getOrNull(1)?.index
                            ?: timeSlots.periods.lastOrNull()?.index
                            ?: 2,
                        colorIndex = 0,
                    )
                }
            } else {
                val course = repository.getCourseById(courseId)
                if (course == null) {
                    _uiState.update { it.copy(errorMessage = "课程不存在") }
                    return@launch
                }
                // 试着把已落库的 startMinutes/endMinutes 翻译回 period index（如果恰好匹配）
                val matchedStart = timeSlots.periods.firstOrNull { it.startMinutes == course.startTime }?.index
                val matchedEnd = timeSlots.periods.firstOrNull { it.endMinutes == course.endTime }?.index
                val guessedMode = if (matchedStart != null && matchedEnd != null) {
                    TimeMode.PERIOD
                } else {
                    TimeMode.CLOCK
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isEdit = true,
                        availableSemesters = semesters,
                        availableTimeSlots = timeSlots,
                        id = course.id,
                        name = course.name,
                        teacher = course.teacher,
                        location = course.location,
                        dayOfWeek = course.dayOfWeek,
                        mode = guessedMode,
                        startMinutes = course.startTime,
                        endMinutes = course.endTime,
                        startPeriodIndex = matchedStart ?: 1,
                        endPeriodIndex = matchedEnd ?: (matchedStart ?: 1) + 1,
                        semester = course.semester ?: currentSem.orEmpty(),
                        weeks = course.weeks.orEmpty(),
                        reminderMinutes = course.reminderMinutes,
                        note = course.note.orEmpty(),
                        creditText = course.credit?.toString().orEmpty(),
                        gradePointText = course.gradePoint?.toString().orEmpty(),
                        colorIndex = CoursePalette.indexOfFirst { c ->
                            c.value.toLong() == course.color.toLong()
                        }.takeIf { idx -> idx >= 0 } ?: 0,
                    )
                }
            }
        }
    }

    fun updateName(v: String) = _uiState.update { it.copy(name = v, errorMessage = null) }
    fun updateTeacher(v: String) = _uiState.update { it.copy(teacher = v) }
    fun updateLocation(v: String) = _uiState.update { it.copy(location = v) }
    fun updateDayOfWeek(v: Int) = _uiState.update { it.copy(dayOfWeek = v) }
    fun updateStartMinutes(v: Int) = _uiState.update { it.copy(startMinutes = v) }
    fun updateEndMinutes(v: Int) = _uiState.update { it.copy(endMinutes = v) }
    fun updateColorIndex(v: Int) = _uiState.update { it.copy(colorIndex = v) }
    fun updateSemester(v: String) = _uiState.update { it.copy(semester = v) }
    fun updateWeeks(v: String) = _uiState.update { it.copy(weeks = v) }
    fun updateReminderMinutes(v: Int) = _uiState.update { it.copy(reminderMinutes = v) }
    fun updateNote(v: String) = _uiState.update { it.copy(note = v) }
    fun updateCreditText(v: String) = _uiState.update { it.copy(creditText = v) }
    fun updateGradePointText(v: String) = _uiState.update { it.copy(gradePointText = v) }

    // ===== 时间模式 =====

    fun setMode(mode: TimeMode) = _uiState.update { it.copy(mode = mode) }
    fun updateStartPeriod(index: Int) = _uiState.update { it.copy(startPeriodIndex = index) }
    fun updateEndPeriod(index: Int) = _uiState.update { it.copy(endPeriodIndex = index) }

    fun save(force: Boolean = false) {
        val state = _uiState.value
        if (!state.isValid || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val color = CoursePalette[state.colorIndex.coerceIn(0, CoursePalette.lastIndex)].value.toInt()
                upsertCourseUseCase(state.toCourse(color), force = force)
                _saved.value = true
            } catch (e: com.example.personalassistant.feature.timetable.domain.usecase.CourseConflictException) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "时间与已有课程冲突（${e.conflict.conflicts.size} 门）",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }

    fun delete() {
        val state = _uiState.value
        if (!state.isEdit || state.id <= 0L) return
        viewModelScope.launch {
            runCatching { repository.deleteCourse(state.id) }
                .onSuccess { _deleted.value = true }
                .onFailure { _uiState.update { s -> s.copy(errorMessage = it.message) } }
        }
    }
}
