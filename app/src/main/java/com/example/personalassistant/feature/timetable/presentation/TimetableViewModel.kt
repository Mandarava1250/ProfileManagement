package com.example.personalassistant.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import com.example.personalassistant.feature.timetable.domain.usecase.CourseConflictException
import com.example.personalassistant.feature.timetable.domain.usecase.DeleteCourseUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveCurrentSemesterUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveCoursesBySemesterUseCase
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
    ) { current, allSemesters, manual ->
        val selectedName = manual ?: current?.name
        Triple(selectedName, allSemesters, current)
    }
        .flatMapLatest { (selectedName, allSemesters, current) ->
            if (selectedName == null) {
                flowOf(TimetableUiState(semesters = allSemesters, isLoading = false))
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
                    TimetableUiState(
                        semesters = allSemesters,
                        currentSemester = effectiveSemester,
                        currentWeekNumber = weekNumber,
                        courses = activeCourses,
                        isLoading = false,
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
}