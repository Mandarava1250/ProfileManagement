package com.example.personalassistant.feature.task.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.model.FilterExpression
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskFilter
import com.example.personalassistant.feature.task.domain.model.TaskGroups
import com.example.personalassistant.feature.task.domain.model.TaskSort
import com.example.personalassistant.feature.task.domain.model.toExpression
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveAllTagsWithCountUseCase
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
 * Phase 6c：
 * - filter 切换到 [FilterExpression] 类型（保留 [TaskFilter] 兼容路径）
 * - 新增 `_selectedTagId` 状态：用户点顶部 tag chip 时切换
 * - 当 selectedTagId 非 null 时，filter 改为 `And(ByTag(id), currentFilter)`
 *   这样 filter 与 tag 共存（ActiveOnly + ByTag = 仅显示未完成且有该标签的任务）
 * - 同时观察 [ObserveAllTagsWithCountUseCase] 拿标签云，写入 uiState.tagsWithCount
 *
 * 实现要点：7 个 Flow 用 [combine] 的可变参数重载 `combine(vararg)` 合并。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskViewModel @Inject constructor(
    observeTasks: ObserveTasksUseCase,
    observeAllTagsWithCount: ObserveAllTagsWithCountUseCase,
    private val toggleTaskComplete: ToggleTaskCompleteUseCase,
    private val deleteTask: DeleteTaskUseCase,
) : ViewModel() {

    private val _filter = MutableStateFlow<FilterExpression>(FilterExpression.ActiveOnly)
    private val _sort = MutableStateFlow(TaskSort.DEFAULT)
    private val _sortMenuExpanded = MutableStateFlow(false)
    private val _showCompleted = MutableStateFlow(false)

    /**
     * Phase 6c：当前选中的标签 id（null = 不过滤）
     */
    private val _selectedTagId = MutableStateFlow<Long?>(null)

    /**
     * 把当前 filter 与 selectedTagId 组合成 effective filter：
     * - tag == null 时直接用 filter
     * - tag != null 时用 And(ByTag(id), filter)
     */
    private val effectiveFilter = combine(_filter, _selectedTagId) { f, tid ->
        if (tid == null) f
        else FilterExpression.And(FilterExpression.ByTag(tid), f)
    }

    /**
     * filter / sort 任一变化都触发最新任务流重订阅
     */
    private val tasksFlow = effectiveFilter
        .flatMapLatest { f -> _sort.flatMapLatest { s -> observeTasks(f, s) } }

    /**
     * Phase 6c：标签云流（独立发射，不参与任务流重订阅）。
     *
     * 单独抽出来避免 combine 泛型推导出问题（kotlinx-coroutines 的 combine
     * 要求所有 Flow 类型相同，最多 5 个泛型参数；超过需要分阶段合并）。
     */
    private val tagsWithCountFlow = observeAllTagsWithCount()

    val uiState: StateFlow<TaskUiState> = combine(
        // 第一阶段：合并 5 个核心流（任务流 + 4 个 UI 状态）
        tasksFlow,
        _showCompleted,
        _filter,
        _sort,
        _sortMenuExpanded,
    ) { tasks, showCompleted, filter, sort, sortMenuExpanded ->
        TasksBundle(tasks, showCompleted, filter, sort, sortMenuExpanded)
    }.let { bundleFlow ->
        // 第二阶段：合并 tags + selectedTagId（统一为 Any?）
        combine(
            bundleFlow,
            tagsWithCountFlow,
            _selectedTagId,
        ) { bundle, tags, selectedTagId ->
            val active = bundle.tasks.filter { !it.isCompleted }
            val completed = bundle.tasks.filter { it.isCompleted }
            val groups = ObserveTasksByQuadrantUseCase.classify(active, completed)
            TaskUiState(
                isLoading = false,
                groups = groups,
                showCompleted = bundle.showCompleted,
                filter = bundle.filter,
                sort = bundle.sort,
                sortMenuExpanded = bundle.sortMenuExpanded,
                tagsWithCount = tags,
                selectedTagId = selectedTagId,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TaskUiState(),
    )

    /**
     * 第一阶段中间产物（不可变 data class，避免长参数列表污染代码可读性）。
     */
    private data class TasksBundle(
        val tasks: List<Task>,
        val showCompleted: Boolean,
        val filter: FilterExpression,
        val sort: TaskSort,
        val sortMenuExpanded: Boolean,
    )

    // ===== Actions =====

    fun setFilter(filter: FilterExpression) {
        _filter.value = filter
    }

    /**
     * Phase 6c 兼容：接旧 [TaskFilter] 的入口（由 UI 适配层 [TaskFilterBar] 调用）。
     *
     * 内部把 [TaskFilter] 转译成 [FilterExpression]（用顶层扩展函数 [TaskFilter.toExpression]）。
     */
    fun setFilter(filter: TaskFilter) {
        _filter.value = filter.toExpression()
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

    // ===== Phase 6c：tag 筛选 =====

    /**
     * 切换 tag 筛选状态：
     * - 已选中该 tag：取消（tagId = null）
     * - 未选中该 tag：应用（tagId = id）
     */
    fun toggleTagFilter(tagId: Long) {
        _selectedTagId.value = if (_selectedTagId.value == tagId) null else tagId
    }

    /** 清空 tag 筛选 */
    fun clearTagFilter() {
        _selectedTagId.value = null
    }
}