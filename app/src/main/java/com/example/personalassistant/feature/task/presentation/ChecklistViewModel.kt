package com.example.personalassistant.feature.task.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.model.ChecklistFilter
import com.example.personalassistant.feature.task.domain.model.ChecklistGroup
import com.example.personalassistant.feature.task.domain.model.ChecklistSort
import com.example.personalassistant.feature.task.domain.model.ChecklistView
import com.example.personalassistant.feature.task.domain.model.SmartList
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.model.applyTo
import com.example.personalassistant.feature.task.domain.model.groupChecklistItems
import com.example.personalassistant.feature.task.domain.model.matches
import com.example.personalassistant.feature.task.domain.model.parseQuickInput
import com.example.personalassistant.feature.task.domain.repository.TaskListRepository
import com.example.personalassistant.feature.task.domain.usecase.AddChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.AddNestedChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.AttachTagToChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.CreateTaskListUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskListUseCase
import com.example.personalassistant.feature.task.domain.usecase.DetachTagFromChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveAllTagsWithCountUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveChecklistItemsUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveTaskListsUseCase
import com.example.personalassistant.feature.task.domain.usecase.RenameTaskListUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.UpdateChecklistItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 清单页 ViewModel
 *
 * ## Phase 15 扩展
 *
 * - 数据源换成清单仓库（带标签 + 嵌套子清单的组合模型）
 * - 支持**部分更新**清单项（标题 / 分类 / 截止时间 / 优先级 / 金额）
 * - 支持**标签挂载 / 解绑**
 * - 支持**嵌套子清单**（addSubTask）
 * - 支持筛选（分类 / 标签）、分组（未完成 / 已完成 / 按分类 / 按截止时间）、排序
 *
 * 筛选 / 分组 / 排序都在内存里做（纯函数，见 [ChecklistFilter]）——
 * 清单量级不大，比推到 SQL 里维护三套查询省事得多。
 */
@HiltViewModel
class ChecklistViewModel @Inject constructor(
    observeChecklistItems: ObserveChecklistItemsUseCase,
    private val addChecklistItem: AddChecklistItemUseCase,
    private val addNestedChecklistItem: AddNestedChecklistItemUseCase,
    private val updateChecklistItem: UpdateChecklistItemUseCase,
    private val toggleChecklistItem: ToggleChecklistItemUseCase,
    private val deleteChecklistItem: DeleteChecklistItemUseCase,
    private val attachTagToItem: AttachTagToChecklistItemUseCase,
    private val detachTagFromItem: DetachTagFromChecklistItemUseCase,
    observeAllTagsWithCount: ObserveAllTagsWithCountUseCase,
    // ===== Phase 17：清单（归属地）=====
    observeTaskLists: ObserveTaskListsUseCase,
    private val createTaskList: CreateTaskListUseCase,
    private val renameTaskList: RenameTaskListUseCase,
    private val deleteTaskList: DeleteTaskListUseCase,
) : ViewModel() {

    /** 当前筛选条件（分类 / 标签 / 分组 / 排序）。 */
    private val filterState = MutableStateFlow(ChecklistFilter())

    /** Phase 17：当前视图（智能清单 or 用户清单）。 */
    private val currentViewState = MutableStateFlow<ChecklistView>(ChecklistView.Default)

    val uiState: StateFlow<ChecklistUiState> = combine(
        observeChecklistItems(),
        filterState,
        observeAllTagsWithCount(),
        currentViewState,
        observeTaskLists(),
    ) { items, filter, tags, view, lists ->
        // Phase 17：先按视图筛（收集箱 / 今天 / 用户清单……），再按分类 / 标签筛
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val inView = items.filter { view.matches(it.task, today, zone) }
        val visible = filter.applyTo(inView)
        ChecklistUiState(
            isLoading = false,
            // 旧 UI 兼容字段：扁平清单项
            items = visible.map { it.task },
            // 新 UI 用这个：带标签 + 嵌套子清单
            itemDetails = visible.map { it.toUi() },
            sections = groupChecklistItems(visible, filter.group).map { it.toUi() },
            filter = filter,
            availableTags = tags,
            currentView = view,
            lists = lists,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChecklistUiState(),
    )

    // ===== Phase 17：视图切换 =====

    /** 切到某个视图（智能清单 / 用户清单）。 */
    fun selectView(view: ChecklistView) {
        currentViewState.value = view
    }

    /** 切到某个智能清单。 */
    fun selectSmartList(smartList: SmartList) {
        selectView(ChecklistView.Smart(smartList))
    }

    /** 切到某个用户清单。 */
    fun selectUserList(listId: Long) {
        selectView(ChecklistView.User(listId))
    }

    /**
     * 新建清单，建完自动切过去 —— 用户刚建完就等着往里放东西。
     */
    fun createList(displayName: String, colorHex: Long? = null) {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                createTaskList(
                    displayName = trimmed,
                    colorHex = colorHex ?: TaskListRepository.DEFAULT_COLOR,
                )
            }.onSuccess { id -> selectUserList(id) }
        }
    }

    /** 重命名清单。 */
    fun renameList(id: Long, displayName: String) {
        viewModelScope.launch {
            runCatching { renameTaskList(id, displayName) }
        }
    }

    /**
     * 删除清单。
     *
     * 删完如果当前正看着这个清单，就退回收集箱 —— 否则页面会停在一个不存在的清单上。
     */
    fun deleteList(id: Long, strategy: DeleteTaskListUseCase.DeleteStrategy) {
        viewModelScope.launch {
            runCatching { deleteTaskList(id, strategy) }
                .onSuccess {
                    val view = currentViewState.value
                    if (view is ChecklistView.User && view.listId == id) {
                        selectSmartList(SmartList.INBOX)
                    }
                }
        }
    }

    /**
     * 当前视图下新建的清单项应该落在哪个清单。
     *
     * - 用户清单视图 → 落在那个清单
     * - 智能清单视图 → 落收集箱（智能清单是「视图」不是「归属地」）
     */
    private fun targetListId(): Long = when (val view = currentViewState.value) {
        is ChecklistView.User -> view.listId
        is ChecklistView.Smart -> TaskList.INBOX_ID
        // 四象限是「换排布」不是「归属地」—— 新建项仍落收集箱
        is ChecklistView.Quadrant -> TaskList.INBOX_ID
    }

    // ===== 增 =====

    /**
     * 新增清单项（只有标题）。
     *
     * 保留单参数签名：现有 UI（`onAdd = checklistViewModel::addItem`）直接用它。
     */
    fun addItem(title: String) {
        addItemWithDetails(title)
    }

    /**
     * 新增清单项（可带分类 / 截止时间 / 优先级 / 金额）。
     *
     * Phase 18：**标题里的自然语言日期会被拆出来**（「买牛奶 明天」→ 标题「买牛奶」+
     * 截止明天 9:00）。拆不出就原样当标题。
     */
    fun addItemWithDetails(
        title: String,
        category: String? = null,
        dueDate: Long? = null,
        priority: TaskPriority = TaskPriority.MEDIUM,
        amount: Double? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                // Phase 18：自然语言日期 —— 只在没显式给截止时间时才从标题里拆
                val parsed = if (dueDate == null) {
                    parseQuickInput(
                        input = title,
                        today = LocalDate.now(),
                        zoneId = ZoneId.systemDefault(),
                    )
                } else {
                    null
                }
                val finalTitle = parsed?.title?.takeIf { it.isNotBlank() } ?: title
                val finalDueDate = dueDate ?: parsed?.dueDate

                addChecklistItem(
                    title = finalTitle,
                    category = category,
                    dueDate = finalDueDate,
                    priority = priority,
                    amount = amount,
                    // Phase 17：落在当前视图对应的清单
                    listId = targetListId(),
                )
            }
        }
    }

    /**
     * 新增**嵌套**子清单项（挂在 [parentSubTaskId] 下面）。
     */
    fun addSubTask(parentSubTaskId: Long, title: String) {
        viewModelScope.launch {
            runCatching { addNestedChecklistItem(parentSubTaskId, title) }
        }
    }

    // ===== 改 =====

    /**
     * 部分更新清单项。
     *
     * 传 null = 不改那个字段；要**清空**分类 / 截止时间 / 金额请用 [clearItemFields]。
     */
    fun updateItem(
        id: Long,
        title: String? = null,
        category: String? = null,
        dueDate: Long? = null,
        priority: TaskPriority? = null,
        amount: Double? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                updateChecklistItem(
                    id = id,
                    title = title,
                    category = category,
                    dueDate = dueDate,
                    priority = priority,
                    amount = amount,
                )
            }
        }
    }

    /**
     * 清空清单项的可空字段（分类 / 截止时间 / 金额）。
     */
    fun clearItemFields(
        id: Long,
        clearCategory: Boolean = false,
        clearDueDate: Boolean = false,
        clearAmount: Boolean = false,
    ) {
        viewModelScope.launch {
            runCatching {
                updateChecklistItem(
                    id = id,
                    clearCategory = clearCategory,
                    clearDueDate = clearDueDate,
                    clearAmount = clearAmount,
                )
            }
        }
    }

    /** 勾选 / 取消勾选。 */
    fun toggleComplete(id: Long, completed: Boolean) {
        viewModelScope.launch {
            runCatching { toggleChecklistItem(id, completed) }
        }
    }

    // ===== 删 =====

    /** 删除清单项。 */
    fun deleteItem(item: Task) {
        viewModelScope.launch {
            runCatching { deleteChecklistItem(item) }
        }
    }

    // ===== 标签 =====

    /** 给清单项挂标签。 */
    fun attachTag(subTaskId: Long, tagId: Long) {
        viewModelScope.launch {
            runCatching { attachTagToItem(subTaskId, tagId) }
        }
    }

    /** 从清单项解绑标签。 */
    fun detachTag(subTaskId: Long, tagId: Long) {
        viewModelScope.launch {
            runCatching { detachTagFromItem(subTaskId, tagId) }
        }
    }

    // ===== 筛选 / 分组 / 排序 =====

    /** 按分类筛选（null = 不筛）。 */
    fun setCategoryFilter(category: String?) {
        filterState.update { it.copy(category = category) }
    }

    /** 按标签筛选（null = 不筛）。 */
    fun setTagFilter(tagId: Long?) {
        filterState.update { it.copy(tagId = tagId) }
    }

    /** 切换分组方式。 */
    fun setGroup(group: ChecklistGroup) {
        filterState.update { it.copy(group = group) }
    }

    /** 切换排序方式。 */
    fun setSort(sort: ChecklistSort) {
        filterState.update { it.copy(sort = sort) }
    }

    /** 清空所有筛选条件（回到默认：不筛 / 不分组 / 手动顺序）。 */
    fun resetFilter() {
        filterState.value = ChecklistFilter()
    }
}
