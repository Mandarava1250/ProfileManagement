package com.example.personalassistant.feature.task.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.model.ChecklistFilter
import com.example.personalassistant.feature.task.domain.model.ChecklistGroup
import com.example.personalassistant.feature.task.domain.model.ChecklistSort
import com.example.personalassistant.feature.task.domain.model.ChecklistView
import com.example.personalassistant.feature.task.domain.model.CompletionStats
import com.example.personalassistant.feature.task.domain.model.NamedFilter
import com.example.personalassistant.feature.task.domain.model.SmartList
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.model.applyTo
import com.example.personalassistant.feature.task.domain.model.groupChecklistItems
import com.example.personalassistant.feature.task.domain.model.matches
import com.example.personalassistant.feature.task.domain.model.parseQuickInput
import com.example.personalassistant.feature.task.domain.model.toExpression
import com.example.personalassistant.feature.task.domain.model.toSpec
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.task.domain.repository.ChecklistDefaultsRepository
import com.example.personalassistant.feature.task.domain.repository.NamedFilterRepository
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
import com.example.personalassistant.feature.task.domain.usecase.ObserveCompletionStatsUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveTaskListsUseCase
import com.example.personalassistant.feature.task.domain.usecase.RecordExpenseFromTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.RestoreChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.RenameTaskListUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleChecklistItemUseCase
import com.example.personalassistant.feature.task.domain.usecase.UpdateChecklistItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    // ===== Phase 19：命名过滤器 =====
    private val namedFilterRepository: NamedFilterRepository,
    // ===== Phase 22.6：完成率统计 =====
    private val observeCompletionStats: ObserveCompletionStatsUseCase,
    // ===== Phase 22.5：新建清单项的默认值 =====
    private val checklistDefaultsRepository: ChecklistDefaultsRepository,
    // ===== Phase 18.7：撤销删除 =====
    private val restoreChecklistItem: RestoreChecklistItemUseCase,
    // ===== Phase 21：清单 ↔ 记账联动 =====
    private val recordExpenseFromTask: RecordExpenseFromTaskUseCase,
    private val financeRepository: FinanceRepository,
) : ViewModel() {

    // ===== Phase 21：清单 ↔ 记账联动 =====

    /**
     * 待记账的清单项（完成带金额的项时弹「记一笔？」）。
     *
     * null = 没有待记账的项（弹窗关着）。
     */
    private val pendingExpenseTaskState = MutableStateFlow<Task?>(null)
    val pendingExpenseTask: StateFlow<Task?> = pendingExpenseTaskState

    /** 支出分类（弹窗里让用户选一个）。 */
    val expenseCategories: StateFlow<List<Category>> = financeRepository
        .observeCategoriesByType(TransactionType.EXPENSE)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * 已经记过账的清单项 id 集合（Phase 21.4 防重复记账）。
     *
     * 靠 `transactions.linkedTaskId` 反查 —— 弱引用，清单项被删了流水还在，
     * 所以这里只当作「这一项记过了，别再问」的标记。
     */
    val expenseRecordedTaskIds: StateFlow<Set<Long>> = financeRepository
        .observeAllTransactions()
        .map { txs -> txs.mapNotNull { it.linkedTaskId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptySet(),
        )

    /**
     * 确认记账：把待记账的清单项记成一笔支出。
     *
     * 分类由用户选 —— 不擅自挑一个（Phase 21.3：分类缺失时让用户选，不静默失败）。
     */
    fun confirmExpense(categoryId: Long) {
        val task = pendingExpenseTaskState.value ?: return
        viewModelScope.launch {
            runCatching { recordExpenseFromTask(task, categoryId) }
            pendingExpenseTaskState.value = null
        }
    }

    /** 放弃记账（「以后再说」）。 */
    fun dismissExpense() {
        pendingExpenseTaskState.value = null
    }

    /** 当前筛选条件（分类 / 标签 / 分组 / 排序）。 */
    private val filterState = MutableStateFlow(ChecklistFilter())

    /** Phase 17：当前视图（智能清单 or 用户清单）。 */
    private val currentViewState = MutableStateFlow<ChecklistView>(ChecklistView.Default)

    /**
     * Phase 19：命名过滤器列表（用户保存的筛选条件组合）。
     *
     * 单独一个 StateFlow 而不是塞进 uiState —— 抽屉只关心这一份，
     * 列表重算时不必带着它一起 combine。
     */
    val namedFilters: StateFlow<List<NamedFilter>> = namedFilterRepository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

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
            // Phase 18.4：全局手动顺序（**不按视图过滤**）—— 拖拽排序靠它保住别项的位置
            allTopLevelIds = items.map { it.task.id },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChecklistUiState(),
    )

    /**
     * Phase 22.6：完成率统计（最近 7 天）。
     *
     * 刻意做成**独立** StateFlow 而不是塞进 [uiState] 的 combine ——
     * uiState 已经有 5 路 combine，再加第 6 路就得用 vararg 版本做类型体操，
     * 而完成率有自己的刷新节奏（按天聚合，跟筛选条件无关）。
     *
     * 刷新触发器 = 任务的 `updatedAt` 最大值 ——
     * 改标题 / 勾完成 / 改截止时间都会让它变。
     */
    val completionStats: StateFlow<CompletionStats> = observeChecklistItems()
        .map { items -> items.maxOfOrNull { it.task.updatedAt } ?: 0L }
        .distinctUntilChanged()
        .map { observeCompletionStats() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CompletionStats(),
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
     *
     * Phase 22.5：**快速添加**会套用设置页里的默认值（默认清单 / 默认优先级 / 默认提醒）。
     * 编辑面板那条路（[addItemWithDetails]）不套 —— 那边用户已经明确选过值了，
     * 再覆盖一遍是添乱。
     */
    fun addItem(title: String) {
        viewModelScope.launch {
            runCatching {
                val defaults = checklistDefaultsRepository.observe().first()
                val parsed = parseQuickInput(
                    input = title,
                    today = LocalDate.now(),
                    zoneId = ZoneId.systemDefault(),
                )
                val finalTitle = parsed?.title?.takeIf { it.isNotBlank() } ?: title
                val finalDueDate = parsed?.dueDate

                // 默认提醒：只在**有截止时间**时才算得出来提醒点
                val reminderTime = defaults.defaultReminderMinutesBefore
                    ?.let { minutes -> finalDueDate?.minus(minutes * 60_000L) }

                addChecklistItem(
                    title = finalTitle,
                    category = null,
                    dueDate = finalDueDate,
                    priority = defaults.defaultPriority,
                    amount = null,
                    // 默认清单：设了就一律落到那儿，没设就跟着当前视图
                    listId = defaults.defaultListId ?: targetListId(),
                    reminderTime = reminderTime,
                )
            }
        }
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
     *
     * Phase 18.6：多了 [listId]（批量「移入清单」要用）。
     */
    fun updateItem(
        id: Long,
        title: String? = null,
        category: String? = null,
        dueDate: Long? = null,
        priority: TaskPriority? = null,
        amount: Double? = null,
        listId: Long? = null,
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
                    listId = listId,
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

    /**
     * 勾选 / 取消勾选。
     *
     * Phase 21：完成**带金额**的清单项时问一句「记一笔？」——
     * 这是滴答的清单 ↔ 记账联动。已经记过账的项不再问（Phase 21.4 防重复）。
     */
    fun toggleComplete(id: Long, completed: Boolean) {
        viewModelScope.launch {
            runCatching { toggleChecklistItem(id, completed) }
            if (!completed) return@launch

            val task = uiState.value.items.firstOrNull { it.id == id }
            val amount = task?.amount ?: return@launch
            if (amount <= 0.0) return@launch
            if (id in expenseRecordedTaskIds.value) return@launch

            pendingExpenseTaskState.value = task
        }
    }

    // ===== 删 =====

    /**
     * Phase 18.7 / 18.8：删除通知（UI 侧弹 Snackbar）。
     *
     * 每次删除都会重新赋值 —— UI 侧靠 `LaunchedEffect` 监听这个值弹 Snackbar。
     */
    private val deleteNoticeState = MutableStateFlow<ChecklistDeleteNotice?>(null)
    val deleteNotice: StateFlow<ChecklistDeleteNotice?> = deleteNoticeState

    /**
     * 删除清单项。
     *
     * Phase 18.8：删之前先数一数它有几个子项 —— 数据层靠 FK SET NULL 把子项
     * **提升为顶层项**（内容不丢），UI 得把这件事说出来。
     */
    fun deleteItem(item: Task) {
        viewModelScope.launch {
            val promoted = promotedChildCount(setOf(item.id))
            // 用数据层的真实对象删 —— 撤销要按原样放回去
            val real = uiState.value.items.firstOrNull { it.id == item.id } ?: item
            runCatching { deleteChecklistItem(real) }
                .onSuccess {
                    // 记住它 —— 用户在 Snackbar 上点「撤销」时要能放回去
                    deleteNoticeState.value = ChecklistDeleteNotice(
                        items = listOf(real),
                        promotedChildCount = promoted,
                    )
                }
        }
    }

    /**
     * Phase 18.7：撤销刚才的删除。
     *
     * 按**原 id** 放回去，恢复后 id 不变；批量删除时逐个放回。
     */
    fun undoDelete() {
        val notice = deleteNoticeState.value ?: return
        viewModelScope.launch {
            notice.items.forEach { item -> runCatching { restoreChecklistItem(item) } }
            deleteNoticeState.value = null
        }
    }

    /** 撤销窗口关闭（或超时）后清掉记录 —— 之后不能再撤。 */
    fun clearDeleteNotice() {
        deleteNoticeState.value = null
    }

    /**
     * Phase 18.8：这些项被删后，有多少个子项会被 **FK SET NULL 提升为顶层项**。
     *
     * 只数**直接子项** —— 孙项跟着各自的父项走，仍然挂在父项下面。
     */
    private fun promotedChildCount(ids: Set<Long>): Int {
        val byId = uiState.value.itemDetails.associateBy { it.id }
        return ids.sumOf { id -> byId[id]?.children?.size ?: 0 }
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

    // ===== Phase 18.6：多选 + 批量操作 =====

    /**
     * 当前选中的清单项 id。
     *
     * 集合非空 = 多选模式（UI 会显示批量操作条，卡片左边的勾选框变成「选中」框）。
     */
    private val selectionState = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = selectionState

    /** 切换某一项的选中状态（长按进入多选 / 点勾选框切换）。 */
    fun toggleSelection(id: Long) {
        selectionState.update { if (id in it) it - id else it + id }
    }

    /** 退出多选模式。 */
    fun clearSelection() {
        selectionState.value = emptySet()
    }

    /** 全选（传入当前视图下的项 id）。 */
    fun selectAll(ids: List<Long>) {
        selectionState.value = ids.toSet()
    }

    /**
     * Phase 18.6：批量改截止时间。
     *
     * @param dueDate null = 清除截止时间
     */
    fun batchSetDueDate(dueDate: Long?) {
        val ids = selectionState.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                runCatching {
                    updateChecklistItem(
                        id = id,
                        dueDate = dueDate,
                        clearDueDate = dueDate == null,
                    )
                }
            }
            clearSelection()
        }
    }

    /** Phase 18.6：批量改优先级。 */
    fun batchSetPriority(priority: TaskPriority) {
        val ids = selectionState.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> runCatching { updateChecklistItem(id = id, priority = priority) } }
            clearSelection()
        }
    }

    /** Phase 18.6：批量移入别的清单（归属地）。 */
    fun batchMoveToList(listId: Long) {
        val ids = selectionState.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> runCatching { updateChecklistItem(id = id, listId = listId) } }
            clearSelection()
        }
    }

    /** Phase 18.6：批量加标签。 */
    fun batchAttachTag(tagId: Long) {
        val ids = selectionState.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id -> runCatching { attachTagToItem(id, tagId) } }
            clearSelection()
        }
    }

    /**
     * Phase 18.6：批量删除。
     *
     * Phase 18.8：删之前先数一数被删项的子项 —— 它们会被 FK SET NULL 提升为顶层项，
     * 所以 Snackbar 里要写明「N 个子项已提升为顶层项」。
     */
    fun batchDelete() {
        val ids = selectionState.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val tasks = resolveTasks(ids)
            val promoted = promotedChildCount(ids)
            tasks.forEach { task -> runCatching { deleteChecklistItem(task) } }
            clearSelection()
            deleteNoticeState.value = ChecklistDeleteNotice(
                items = tasks,
                promotedChildCount = promoted,
            )
        }
    }

    /**
     * 选中项 → 可删除的 [Task]。
     *
     * 顶层项用数据层的真实对象（字段全，撤销能原样放回去）；
     * 嵌套子项在扁平列表里没有，用 UI 模型还原（删除只按主键匹配，够用）。
     */
    private fun resolveTasks(ids: Set<Long>): List<Task> {
        val flat = uiState.value.items.associateBy { it.id }
        val tree = uiState.value.itemDetails
        return ids.mapNotNull { id ->
            flat[id] ?: findInTree(tree, id)?.toSubTask()
        }
    }

    /** 在「顶层项 + 嵌套子项」的树里找某个 id。 */
    private fun findInTree(items: List<ChecklistItemUi>, id: Long): ChecklistItemUi? {
        for (item in items) {
            if (item.id == id) return item
            findInTree(item.children, id)?.let { return it }
        }
        return null
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

    // ===== Phase 19：命名过滤器 =====

    /**
     * 把**当前筛选条件**存成命名过滤器。
     *
     * 同名会覆盖旧的 —— 用户改完条件重新存同名是自然操作。
     *
     * @param name 过滤器名字（空名字直接忽略，不给用户存一条无名过滤器）
     */
    fun saveNamedFilter(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val expression = filterState.value.toExpression()
        viewModelScope.launch {
            runCatching {
                namedFilterRepository.save(
                    NamedFilter(
                        name = trimmed,
                        expression = expression.toSpec(),
                        sortOrder = namedFilters.value.size,
                    ),
                )
            }
        }
    }

    /**
     * 套用一个命名过滤器。
     *
     * 会**替换**掉当前的分类 / 标签筛选 —— 命名过滤器就是「一组筛选条件」，
     * 跟手动的分类 / 标签筛选混在一起会让用户搞不清当前到底筛了什么。
     * 分组和排序保留（那是排布，不是筛选）。
     */
    fun applyNamedFilter(filter: NamedFilter) {
        filterState.update { current ->
            current.copy(
                category = null,
                tagId = null,
                expression = filter.expression.toExpression(),
            )
        }
    }

    /** 取消套用命名过滤器（保留手动分类 / 标签筛选）。 */
    fun clearNamedFilter() {
        filterState.update { it.copy(expression = null) }
    }

    /** 删除一个命名过滤器。 */
    fun deleteNamedFilter(name: String) {
        viewModelScope.launch {
            runCatching { namedFilterRepository.delete(name) }
        }
    }
}
