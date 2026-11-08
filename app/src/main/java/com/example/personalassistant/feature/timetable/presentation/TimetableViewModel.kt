package com.example.personalassistant.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import com.example.personalassistant.feature.timetable.domain.usecase.CourseConflictException
import com.example.personalassistant.feature.timetable.domain.usecase.CreateSemesterUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.DeleteCourseUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveCurrentSemesterUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveCoursesBySemesterUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveTimeSlotScheduleUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ResetTimeSlotScheduleUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.SaveTimeSlotScheduleUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.SwitchSemesterUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.UpsertCourseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 课表主页面 ViewModel
 *
 * 职责：
 *   - 监听学期/课程变化，自动更新 UI 状态
 *   - 计算当前周次（基于 currentSemester.startDate）
 *   - 处理学期切换、课程增删改、冲突提示、删除确认
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val observeCurrentSemester: ObserveCurrentSemesterUseCase,
    private val observeCoursesBySemester: ObserveCoursesBySemesterUseCase,
    private val upsertCourseUseCase: UpsertCourseUseCase,
    private val deleteCourseUseCase: DeleteCourseUseCase,
    private val switchSemesterUseCase: SwitchSemesterUseCase,
    private val createSemesterUseCase: CreateSemesterUseCase,
    private val observeTimeSlotSchedule: ObserveTimeSlotScheduleUseCase,
    private val saveTimeSlotSchedule: SaveTimeSlotScheduleUseCase,
    private val resetTimeSlotSchedule: ResetTimeSlotScheduleUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** 用户手动选择的周次；null 表示跟随 currentSemester 自动计算 */
    private val _manualWeekNumber = MutableStateFlow<Int?>(null)

    /** 用户手动选择的学期；null 表示跟随 currentSemester */
    private val _manualSemesterName = MutableStateFlow<String?>(null)

    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _pendingConflict = MutableStateFlow<Course?>(null)
    private val _showDeleteConfirmId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<TimetableUiState> = combine(
        observeCurrentSemester(),
        repository.observeAllSemesters(),
        _manualSemesterName,
        observeTimeSlotSchedule(),
    ) { current, allSemesters, manual, timeSlots ->
        TimetableUiState(
            semesters = allSemesters,
            currentSemester = current,
            timeSlotSchedule = timeSlots,
            isLoading = false,
        ) to Triple(current, allSemesters, manual)
    }
        .flatMapLatest { (initial, triple) ->
            val (current, allSemesters, manual) = triple
            val selectedName = manual ?: current?.name
            if (selectedName == null) {
                flowOf(initial)
            } else {
                combine(
                    observeCoursesBySemester(selectedName),
                    _manualWeekNumber,
                ) { allCourses, manualWeek ->
                    val effectiveSemester = allSemesters.firstOrNull { it.name == selectedName }
                    val weekNumber = manualWeek ?: effectiveSemester?.let {
                        DateUtils.currentWeekNumber(it.startDate)
                    } ?: 1
                    val activeCourses = allCourses
                        .filter { it.appliesToWeek(weekNumber) }
                        .sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
                    initial.copy(
                        currentSemester = effectiveSemester,
                        currentWeekNumber = weekNumber,
                        // manualWeek 为 null = 没手动翻过周 = 跟着真实本周走。
                        isCurrentWeek = manualWeek == null,
                        courses = activeCourses,
                    )
                }
            }
        }
        .combine(_errorMessage) { state, err -> state.copy(errorMessage = err) }
        .combine(_pendingConflict) { state, pending -> state.copy(pendingConflict = pending) }
        .combine(_showDeleteConfirmId) { state, id -> state.copy(showDeleteConfirmId = id) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TimetableUiState(),
        )

    // ===== 用户事件 =====

    /** 切换学期 */
    fun switchSemester(name: String) {
        viewModelScope.launch {
            runCatching { switchSemesterUseCase(name) }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    /** 切换显示的周次（手动模式） */
    fun selectWeek(weekNumber: Int) {
        _manualWeekNumber.value = weekNumber
    }

    /** 回到当前周 */
    fun jumpToCurrentWeek() {
        _manualWeekNumber.value = null
    }

    // ==================== Phase 26：双视图 ====================

    /**
     * 当前视图（时间线 / 整周网格）。
     *
     * 走**独立** StateFlow 而不是塞进 `uiState`：那条 combine 里有 `flatMapLatest`，
     * 往里塞新字段会牵动整个订阅链重建。视图类型是纯 UI 状态，
     * 也不需要跟着数据库变化重算，单独一条成本最低。
     */
    private val _viewType = MutableStateFlow(TimetableViewType.TIMELINE)
    val viewType: StateFlow<TimetableViewType> = _viewType.asStateFlow()

    fun setViewType(type: TimetableViewType) {
        _viewType.value = type
    }

    /**
     * 时间线视图当前选中的星期（1..7）。
     *
     * 独立 StateFlow，同上。
     */
    private val _selectedDay = MutableStateFlow(1)
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    fun selectDay(day: Int) {
        _selectedDay.value = day.coerceIn(1, 7)
    }

    /** 保存课程（含冲突处理：抛 CourseConflictException 时进入"是否强制"分支） */
    fun saveCourse(course: Course, force: Boolean = false) {
        viewModelScope.launch {
            try {
                upsertCourseUseCase(course, force = force)
                _pendingConflict.value = null
            } catch (e: CourseConflictException) {
                _pendingConflict.value = e.conflict.newCourse
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    /** 用户已确认冲突，确认强制保存 */
    fun confirmSaveWithConflict() {
        val pending = _pendingConflict.value ?: return
        saveCourse(pending, force = true)
    }

    /** 取消冲突对话框 */
    fun dismissConflictDialog() {
        _pendingConflict.value = null
    }

    /** 请求删除课程（先弹出确认） */
    fun requestDelete(id: Long) {
        _showDeleteConfirmId.value = id
    }

    /** 用户确认删除 */
    fun confirmDelete() {
        val id = _showDeleteConfirmId.value ?: return
        viewModelScope.launch {
            runCatching { deleteCourseUseCase(id) }
                .onFailure { _errorMessage.value = it.message }
            _showDeleteConfirmId.value = null
        }
    }

    /** 取消删除 */
    fun dismissDeleteConfirm() {
        _showDeleteConfirmId.value = null
    }

    /** 清空错误消息（Snackbar 显示完成后调用） */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 新建一个学期（UI 弹窗提交时调用）。
     *
     * - name 必填，startDate/endDate 接受 epoch millis
     * - makeCurrent=true：新建后自动成为当前学期，并把手动选学期状态清空
     *   （让观察流自然切到新学期）
     * - 失败（空 name / 日期非法）→ errorMessage 走 Snackbar
     */
    fun addSemester(name: String, startDate: Long, endDate: Long, makeCurrent: Boolean = true) {
        viewModelScope.launch {
            runCatching { createSemesterUseCase(name, startDate, endDate, makeCurrent) }
                .onSuccess {
                    if (makeCurrent) {
                        _manualSemesterName.value = null
                    }
                }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    // ===== 课时表 =====

    /**
     * 保存课时表（编辑对话框提交时调用）。
     *
     * - 校验：依赖 [TimeSlotSchedule] / [Period] init，非法值会抛 IllegalArgumentException
     * - 持久化：DataStore 写入后 uiState 走 `observeTimeSlotSchedule` 自动重发，
     *   TimetableGrid 立刻按新课时表重新算行高
     */
    fun saveTimeSlots(schedule: TimeSlotSchedule) {
        viewModelScope.launch {
            runCatching { saveTimeSlotSchedule.invoke(schedule) }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    /**
     * 重置为内置默认 12 节课时表。
     */
    fun resetTimeSlots() {
        viewModelScope.launch {
            runCatching { resetTimeSlotSchedule.invoke() }
                .onFailure { _errorMessage.value = it.message }
        }
    }
}