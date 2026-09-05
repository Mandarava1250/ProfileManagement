package com.example.personalassistant.feature.task.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveTasksByQuadrantUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveTasksUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleTaskCompleteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 任务主页 ViewModel
 *
 * - Phase 3：观察仓库的 active + completed Flow，合并成分组结果
 * - Phase 4b：
 *   - 引入 filter / sort StateFlow（用户切换 chip / 排序菜单时更新）
 *   - 用 [ObserveTasksUseCase] 拿 filter+sort 后的全量任务流
 *   - 在 ViewModel 内调 [ObserveTasksByQuadrantUseCase.classify] 把 active/completed 分组（保留四象限 UI）
 *   - 维护"是否展开已完成"折叠区 + 排序菜单展开态
 *
 * 这样既能让 filter chip / 排序生效，又保留了 P1-P4 四象限分组显示。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskViewModel @Inject constructor(
    observeTasks: ObserveTasksUseCase,
    private val toggleTaskComplete: ToggleTaskCompleteUseCase,
    private val deleteTask: DeleteTaskUseCase,
) : ViewModel() {

    private val _filter = MutableStateFlow<TaskFilter>(TaskFilter.ActiveOnly)
    private val _sort = MutableStateFlow(TaskSort.DEFAULT)
    private val _sortMenuExpanded = MutableStateFlow(false)
    private val _showCompleted = MutableStateFlow(false)

    /**
     * filter / sort 任一变化都触发最新任务流重订阅
     */
    private val tasksFlow = _filter
        .flatMapLatest { f -> _sort.flatMapLatest { s -> observeTasks(f, s) } }

    val uiState: StateFlow<TaskUiState> = combine(
        tasksFlow,
        _showCompleted,
        _filter,
        _sort,
        _sortMenuExpanded,
    ) { tasks, showCompleted, filter, sort, sortMenuExpanded ->
        val active = tasks.filter { !it.isCompleted }
        val completed = tasks.filter { it.isCompleted }
        val groups = ObserveTasksByQuadrantUseCase.classify(active, completed)
        TaskUiState(
            isLoading = false,
            groups = groups,
            showCompleted = showCompleted,
            filter = filter,
            sort = sort,
            sortMenuExpanded = sortMenuExpanded,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TaskUiState(),
    )

    // ===== Actions =====

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun setSort(sort: TaskSort) {
        _sort.value = sort
    }

    fun toggleSortMenu() {
        _sortMenuExpanded.value = !_sortMenuExpanded.value
    }

    fun dismissSortMenu() {
        _sortMenuExpanded.value = false
    }

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
