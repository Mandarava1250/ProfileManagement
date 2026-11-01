package com.example.personalassistant.feature.timetable.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.timetable.domain.model.Period
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import com.example.personalassistant.feature.timetable.domain.usecase.CreateSemesterUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveCurrentSemesterUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ObserveTimeSlotScheduleUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.ResetTimeSlotScheduleUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.SaveTimeSlotScheduleUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.SwitchSemesterUseCase
import com.example.personalassistant.feature.timetable.domain.usecase.UpdateSemesterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 课表设置页 ViewModel
 *
 * 负责：观察"数据库真值" → 暴露给 UI 作"草稿"（uiState.editingSemesters / editingSchedule），
 * UI 编辑时改的是"草稿"副本，保存时再写回 DB。
 *
 * 这样设计的好处：
 *  - 取消编辑零成本（直接关掉页面，DB 不会被脏改）
 *  - 编辑期间可以独立校验"未保存"的草稿，不必污染 DB
 *  - 校验失败不阻止其他字段编辑（每个 PeriodEditRow 自带 errorMessage）
 */
@HiltViewModel
class TimetableSettingsViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val observeCurrentSemester: ObserveCurrentSemesterUseCase,
    private val observeTimeSlotSchedule: ObserveTimeSlotScheduleUseCase,
    private val createSemesterUseCase: CreateSemesterUseCase,
    private val switchSemesterUseCase: SwitchSemesterUseCase,
    private val updateSemesterUseCase: UpdateSemesterUseCase,
    private val saveTimeSlotScheduleUseCase: SaveTimeSlotScheduleUseCase,
    private val resetTimeSlotScheduleUseCase: ResetTimeSlotScheduleUseCase,
) : ViewModel() {

    /**
     * 草稿状态：用户改的是这里，不直接改 DB
     * - editingSemesters：从 DB 复制过来后用户增删改的对象列表
     * - editingSchedule：用户编辑中的课时表
     */
    private data class Draft(
        val semesters: List<Semester>,
        val schedule: TimeSlotSchedule,
    )

    private val _editingSemesters = MutableStateFlow<List<Semester>>(emptyList())
    private val _editingSchedule = MutableStateFlow(TimeSlotSchedule.default())
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _toast = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TimetableSettingsUiState> = combine(
        // 5 个流以内，combine 走最直白的 lambda 重载
        observeCurrentSemester(),
        repository.observeAllSemesters(),
        observeTimeSlotSchedule(),
        combine(_editingSemesters, _editingSchedule) { sems, sched -> Draft(sems, sched) },
        _errorMessage,
    ) { current, allSemesters, schedule, draft, errorMessage ->
        // 首次进入页面时把 DB 真值同步到草稿（之后用户改了草稿就保留草稿）
        if (draft.semesters.isEmpty() && allSemesters.isNotEmpty()) {
            _editingSemesters.value = allSemesters
        }
        if (draft.schedule == TimeSlotSchedule.default() && schedule != TimeSlotSchedule.default()) {
            _editingSchedule.value = schedule
        }
        TimetableSettingsUiState(
            semesters = allSemesters,
            currentSemester = current,
            editingSemesters = if (draft.semesters.isNotEmpty()) draft.semesters else allSemesters,
            editingSchedule = draft.schedule,
            isLoading = false,
            errorMessage = errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimetableSettingsUiState(),
    )

    val toast: StateFlow<String?> = _toast

    // ===== 学期操作 =====

    fun switchToSemester(name: String) {
        viewModelScope.launch {
            runCatching { switchSemesterUseCase(name) }
                .onSuccess { _toast.value = "已切到 $name" }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun deleteSemester(name: String) {
        viewModelScope.launch {
            runCatching { repository.deleteSemester(name) }
                .onSuccess {
                    // 同步从草稿里剔除
                    _editingSemesters.value = _editingSemesters.value.filterNot { it.name == name }
                    _toast.value = "已删除 $name"
                }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun addSemester(name: String, startDate: Long, endDate: Long) {
        viewModelScope.launch {
            runCatching { createSemesterUseCase(name, startDate, endDate, makeCurrent = true) }
                .onSuccess { _toast.value = "已创建 $name" }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    /**
     * 编辑学期：改 name / startDate / endDate
     *
     * 重命名时如果该学期是当前学期，UI 需要刷一下 current 状态；
     * [SwitchSemesterUseCase] 不会自动触发，但 Switch 一次同 newName 等效
     * "重置"当前标志。
     */
    fun updateSemester(oldName: String, newName: String, startDate: Long, endDate: Long) {
        viewModelScope.launch {
            runCatching { updateSemesterUseCase(oldName, newName, startDate, endDate) }
                .onSuccess { _toast.value = "已保存" }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun updateEditingSemester(oldName: String, newSemester: Semester) {
        // 删除旧名 + 加入新名（name 可能改了）
        _editingSemesters.value = _editingSemesters.value
            .filterNot { it.name == oldName }
            .plus(newSemester)
    }

    // ===== 课时表操作 =====

    /** 在末尾追加一节（默认 18:00-18:45） */
    fun addPeriodRow() {
        val current = _editingSchedule.value
        if (current.periods.size >= 20) {
            _errorMessage.value = "课时数已达上限 20"
            return
        }
        val nextIndex = current.periods.size + 1
        val newPeriod = Period(nextIndex, 18 * 60, 18 * 60 + 45)
        val newList = current.periods + newPeriod
        tryUpdateSchedule(current.copy(periods = newList))
    }

    /** 删除某一行（保持其他行 index 连续） */
    fun removePeriodRow(index: Int) {
        val current = _editingSchedule.value
        val renumbered = current.periods
            .filterNot { it.index == index }
            .mapIndexed { i, p -> p.copy(index = i + 1) }
        tryUpdateSchedule(current.copy(periods = renumbered))
    }

    /** 更新某一行的某个字段（hour/minute 之一） */
    fun updatePeriodRow(index: Int, startHour: Int? = null, startMinute: Int? = null, endHour: Int? = null, endMinute: Int? = null) {
        val current = _editingSchedule.value
        val newList = current.periods.map { p ->
            if (p.index == index) {
                val newStart = (startHour ?: p.startMinutes / 60) * 60 + (startMinute ?: p.startMinutes % 60)
                val newEnd = (endHour ?: p.endMinutes / 60) * 60 + (endMinute ?: p.endMinutes % 60)
                p.copy(startMinutes = newStart, endMinutes = newEnd)
            } else p
        }
        tryUpdateSchedule(current.copy(periods = newList))
    }

    /** 重置为默认 12 节 */
    fun resetTimeSlots() {
        viewModelScope.launch {
            runCatching { resetTimeSlotScheduleUseCase() }
                .onSuccess { _toast.value = "已重置为默认 12 节" }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    /** 保存当前草稿 */
    fun save() {
        val current = _editingSchedule.value
        viewModelScope.launch {
            runCatching { saveTimeSlotScheduleUseCase(current) }
                .onSuccess { _toast.value = "已保存" }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearToast() {
        _toast.value = null
    }

    /**
     * 内部：把 [TimeSlotSchedule] 写入草稿状态
     *
     * 注：这里不调 init 校验（init 会在保存时跑）；UI 草稿里允许"暂未合法"
     * 状态（比如刚加一节就保存），保存时才被 [TimeSlotSchedule.init] 挡住。
     */
    private fun tryUpdateSchedule(schedule: TimeSlotSchedule) {
        // index 重排（保证 1..N 连续）+ 按 startMinutes 排序
        val renumbered = schedule.periods
            .sortedBy { it.startMinutes }
            .mapIndexed { i, p -> p.copy(index = i + 1) }
        _editingSchedule.value = schedule.copy(periods = renumbered)
    }
}
