package com.example.personalassistant.feature.task.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveTasksByQuadrantUseCase
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
 * 任务主页 ViewModel
 *
 * - 观察仓库的 active + completed Flow，合并成分组结果
 * - 维护"是否展开已完成"折叠区的本地状态
 * - toggleComplete / deleteTask 通过 UseCase 转发
 */
@HiltViewModel
class TaskViewModel @Inject constructor(
    observeTasks: ObserveTasksByQuadrantUseCase,
    private val toggleTaskComplete: ToggleTaskCompleteUseCase,
    private val deleteTask: DeleteTaskUseCase,
) : ViewModel() {

    private val _showCompleted = MutableStateFlow(false)

    val uiState: StateFlow<TaskUiState> = combine(
        observeTasks(),
        _showCompleted,
    ) { groups, showCompleted ->
        TaskUiState(isLoading = false, groups = groups, showCompleted = showCompleted)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TaskUiState(),
    )

    fun toggleComplete(id: Long, completed: Boolean) {
        viewModelScope.launch { toggleTaskComplete(id, completed) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { deleteTask(task) }
    }

    fun toggleShowCompleted() {
        _showCompleted.value = !_showCompleted.value
    }
}
