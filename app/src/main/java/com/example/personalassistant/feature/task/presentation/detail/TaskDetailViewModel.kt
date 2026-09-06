package com.example.personalassistant.feature.task.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import com.example.personalassistant.feature.task.domain.usecase.AddSubTaskToTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteSubTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveSubTasksUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleSubTaskCompleteUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleTaskCompleteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 6b：任务详情页 ViewModel
 *
 * 数据流：
 * - [TaskRepository.observeTaskById] + [ObserveSubTasksUseCase] → combine → UiState
 * - task == null 时 UiState.exists = false，UI 监听到 popBackStack（任务被删除）
 *
 * 子清单可增删改（toggle / delete / add），无需跳编辑页即可维护。
 * 任务本身的字段（标题/描述/优先级/...）需要在编辑页改，详情页是只读视图。
 *
 * Phase 6b：open + protected state 让测试 fork 子类喂初始 state。
 */
@HiltViewModel
open class TaskDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSubTasks: ObserveSubTasksUseCase,
    private val repository: TaskRepository,
    private val toggleTaskComplete: ToggleTaskCompleteUseCase,
    private val toggleSubTaskCompleteUseCase: ToggleSubTaskCompleteUseCase,
    private val deleteSubTaskUseCase: DeleteSubTaskUseCase,
    private val addSubTaskToTaskUseCase: AddSubTaskToTaskUseCase,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>(ARG_TASK_ID) ?: -1L

    private val _newSubTaskInput = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)

    /**
     * UiState 聚合：task + subTasks + 输入框 + errorMessage
     *
     * Phase 6b：`open` 让 Compose UI 测试 fork 子类直接喂初值（避免依赖 combine + repository 流）
     */
    open val uiState: StateFlow<TaskDetailUiState> = combine(
        repository.observeTaskById(taskId),
        observeSubTasks(taskId),
        _newSubTaskInput,
        _errorMessage,
    ) { task, subTasks, input, error ->
        TaskDetailUiState(
            isLoading = false,
            task = task,
            subTasks = subTasks,
            newSubTaskInput = input,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TaskDetailUiState(isLoading = true),
    )

    // ===== Actions =====

    fun onNewSubTaskInputChange(value: String) {
        _newSubTaskInput.value = value
    }

    fun addSubTask() {
        val title = _newSubTaskInput.value.trim()
        if (title.isEmpty() || taskId <= 0L) return
        viewModelScope.launch {
            try {
                addSubTaskToTaskUseCase(taskId, title)
                _newSubTaskInput.value = ""
            } catch (e: IllegalArgumentException) {
                _errorMessage.value = e.message
            } catch (e: Exception) {
                _errorMessage.value = "添加失败：${e.message ?: "未知错误"}"
            }
        }
    }

    fun toggleComplete(completed: Boolean) {
        if (taskId <= 0L) return
        viewModelScope.launch { toggleTaskComplete(taskId, completed) }
    }

    fun toggleSubTaskComplete(subTaskId: Long, completed: Boolean) {
        viewModelScope.launch { toggleSubTaskCompleteUseCase(subTaskId, completed) }
    }

    fun deleteSubTask(subTask: com.example.personalassistant.feature.task.domain.model.SubTask) {
        viewModelScope.launch { deleteSubTaskUseCase(subTask) }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    companion object {
        /**
         * SavedStateHandle 参数 key，与 [com.example.personalassistant.navigation.Screen.DetailTask.ARG_TASK_ID] 同值。
         * 详情页 ViewModel 独立持有常量（不直接依赖 navigation.Screen）以避免循环依赖。
         */
        const val ARG_TASK_ID = "taskId"
    }
}
