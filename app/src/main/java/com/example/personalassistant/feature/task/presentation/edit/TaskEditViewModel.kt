package com.example.personalassistant.feature.task.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.model.RepeatRule
import com.example.personalassistant.feature.task.domain.model.RepeatType
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.usecase.CancelReminderUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.GetTaskWithSubTasksUseCase
import com.example.personalassistant.feature.task.domain.usecase.ScheduleReminderUseCase
import com.example.personalassistant.feature.task.domain.usecase.UpsertTaskUseCase
import com.example.personalassistant.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 任务编辑页 ViewModel
 *
 * - 构造时从 SavedStateHandle 拿 taskId
 *   - taskId < 0L：新建模式
 *   - taskId > 0L：编辑模式，初始加载 task + subTasks
 * - save() 调 UpsertTaskUseCase，成功后 isSaved = true 触发页面返回
 * - delete() 调 DeleteTaskUseCase，成功后 isSaved = true
 *
 * Phase 4a 新增接线：
 * - save() 成功后调 ScheduleReminderUseCase（按 reminderTime 决定调度）
 * - delete() 成功后调 CancelReminderUseCase
 * - RepeatRule 在 save 时通过 RepeatRule.toRepeatType() 转回 RepeatType enum 写库
 */
@HiltViewModel
class TaskEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTaskWithSubTasks: GetTaskWithSubTasksUseCase,
    private val upsertTask: UpsertTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val scheduleReminder: ScheduleReminderUseCase,
    private val cancelReminder: CancelReminderUseCase,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>(Screen.EditTask.ARG_TASK_ID) ?: -1L
    private val isNew: Boolean = taskId <= 0L

    private val _uiState = MutableStateFlow(TaskEditUiState(isNew = isNew))
    val uiState: StateFlow<TaskEditUiState> = _uiState.asStateFlow()

    init {
        if (isNew) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                val result = getTaskWithSubTasks(taskId)
                if (result == null) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "任务不存在")
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = result.task.title,
                        description = result.task.description.orEmpty(),
                        priority = result.task.priority,
                        dueDate = result.task.dueDate,
                        subTasks = result.subTasks,
                        category = result.task.category,
                        // Phase 4b：从 repeatType + repeatDays 共同还原 RepeatRule
                        repeatRule = RepeatRule.fromRepeatType(
                            result.task.repeatType,
                            customDays = RepeatRule.parseDaysString(result.task.repeatDays),
                        ),
                        reminderTime = result.task.reminderTime,
                    )
                }
            }
        }
    }

    // ===== 文本字段 =====

    fun onTitleChange(value: String) {
        _uiState.update {
            it.copy(title = value, titleError = if (value.isBlank()) "标题不能为空" else null)
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    // ===== 优先级 =====

    fun onPriorityChange(priority: TaskPriority) {
        _uiState.update { it.copy(priority = priority) }
    }

    // ===== 截止日期 =====

    fun toggleDatePicker() {
        _uiState.update { it.copy(showDatePicker = !it.showDatePicker) }
    }

    fun onDateSelected(dateMillis: Long?) {
        _uiState.update { it.copy(dueDate = dateMillis, showDatePicker = false) }
    }

    fun clearDueDate() {
        _uiState.update { it.copy(dueDate = null) }
    }

    // ===== 分类 =====

    fun onCategoryChange(categoryName: String?) {
        _uiState.update { it.copy(category = categoryName, showCategorySheet = false) }
    }

    fun toggleCategorySheet() {
        _uiState.update { it.copy(showCategorySheet = !it.showCategorySheet) }
    }

    // ===== 重复规则 =====

    fun onRepeatRuleChange(rule: RepeatRule) {
        _uiState.update { it.copy(repeatRule = rule, showRepeatRuleSheet = false) }
    }

    fun toggleRepeatRuleSheet() {
        _uiState.update { it.copy(showRepeatRuleSheet = !it.showRepeatRuleSheet) }
    }

    // ===== 提醒 =====

    fun onReminderChange(millis: Long?) {
        _uiState.update { it.copy(reminderTime = millis, showReminderPicker = false) }
    }

    fun clearReminder() {
        _uiState.update { it.copy(reminderTime = null) }
    }

    fun toggleReminderPicker() {
        _uiState.update { it.copy(showReminderPicker = !it.showReminderPicker) }
    }

    // ===== 子清单 =====

    fun onNewSubTaskInputChange(value: String) {
        _uiState.update { it.copy(newSubTaskInput = value) }
    }

    fun addSubTask() {
        val title = _uiState.value.newSubTaskInput.trim()
        if (title.isEmpty()) return
        val item = SubTask(
            parentTaskId = if (isNew) null else taskId,
            title = title,
            order = _uiState.value.subTasks.size,
        )
        _uiState.update {
            it.copy(
                subTasks = it.subTasks + item,
                newSubTaskInput = "",
            )
        }
    }

    fun toggleSubTaskComplete(id: Long) {
        _uiState.update { state ->
            state.copy(
                subTasks = state.subTasks.map { item ->
                    if (item.id == id) item.copy(isCompleted = !item.isCompleted) else item
                },
            )
        }
    }

    fun deleteSubTask(id: Long) {
        _uiState.update { state ->
            state.copy(subTasks = state.subTasks.filter { it.id != id })
        }
    }

    // ===== 保存 / 删除 =====

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "标题不能为空") }
            return
        }
        viewModelScope.launch {
            try {
                val (repeatType, customDays) = RepeatRule.toRepeatType(state.repeatRule)
                val task = Task(
                    id = if (isNew) 0L else taskId,
                    title = state.title.trim(),
                    description = state.description.takeIf { it.isNotBlank() },
                    priority = state.priority,
                    dueDate = state.dueDate,
                    category = state.category,
                    reminderTime = state.reminderTime,
                    repeatType = repeatType,
                    // Phase 4b：把 CustomWeekdays 序列化为 "MON,..." 字符串，让自定义星期真持久化
                    repeatDays = RepeatRule.formatDaysString(customDays),
                )
                val newRowId = upsertTask(task, state.subTasks)
                // 调度提醒（reminderTime 为 null 时 ScheduleReminderUseCase 内部 no-op）
                // 新建场景下用返回的 rowId 作为 reminder 的 stable id
                scheduleReminder(task.copy(id = newRowId))
                _uiState.update { it.copy(isSaved = true) }
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(titleError = e.message ?: "保存失败") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "保存失败") }
            }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            try {
                val task = Task(id = taskId, title = _uiState.value.title)
                deleteTask(task)
                cancelReminder(taskId)
                _uiState.update { it.copy(isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "删除失败") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
