package com.example.personalassistant.feature.task.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.EmptyState
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.feature.task.domain.model.ChecklistGroup
import com.example.personalassistant.feature.task.domain.model.ChecklistSort
import com.example.personalassistant.feature.task.domain.model.CompletionStats
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.model.parseQuickInput
import com.example.personalassistant.feature.task.domain.model.removeTokenRange
import com.example.personalassistant.feature.task.presentation.components.ChecklistBatchBar
import com.example.personalassistant.feature.task.presentation.components.ChecklistBatchDateSheet
import com.example.personalassistant.feature.task.presentation.components.ChecklistBatchDeleteDialog
import com.example.personalassistant.feature.task.presentation.components.ChecklistBatchListSheet
import com.example.personalassistant.feature.task.presentation.components.ChecklistBatchPrioritySheet
import com.example.personalassistant.feature.task.presentation.components.ChecklistBatchTagSheet
import com.example.personalassistant.feature.task.presentation.components.ChecklistItemCard
import com.example.personalassistant.feature.task.presentation.components.CompletionStatsCard
import com.example.personalassistant.feature.task.presentation.components.FilterEntryButton
import com.example.personalassistant.feature.task.presentation.components.QuickInputChipRow
import com.example.personalassistant.feature.task.presentation.list.ReorderableTaskListViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * 清单 Tab 内容（Phase 15 重做首屏）。
 *
 * ## 首屏结构
 *
 * 1. 顶部一行：快速添加输入框 + 「详细添加」+「快速添加」+ 筛选入口（带生效条件计数徽标）
 * 2. 分组标题 + 分隔线（按当前分组方式渲染 [ChecklistUiState.sections]）
 * 3. 清单项卡片（分类色点 / 标签 / 截止 / 金额 / 优先级，子清单可展开）
 *
 * ## 为什么不自己画一遍筛选面板
 *
 * 筛选 / 分组 / 排序全部收进 [ChecklistFilterSheet]（复用任务页 `TaskFilterSheet` 的视觉语言），
 * 首屏只留一个入口按钮 + 徽标 —— 徽标告诉用户「现在有几个条件生效着」。
 *
 * ## Phase 18.4 / 18.6：拖拽排序与多选
 *
 * - **拖拽排序**：长按后竖直拖动。复用任务页那套 [ReorderableTaskListViewModel]
 *   （拖拽中的本地顺序 + 结束时一次性落库 `TaskRepository.reorderTasks`）。
 *   本地顺序存的是**全局**顺序（[ChecklistUiState.allTopLevelIds]），
 *   所以在某个清单里拖动时，其它清单里的项不会被挤位。
 * - **多选**：长按后抬手进入多选（见 [com.example.personalassistant.feature.task.presentation.components.ChecklistItemGestureBox]），
 *   顶部出现批量操作条：改日期 / 改优先级 / 移入清单 / 加标签 / 删除。
 *
 * @param state 清单页状态
 * @param onToggleComplete 勾选 / 取消勾选
 * @param onDelete 删除清单项
 * @param onAdd 快速添加（只有标题）
 * @param onSaveItem 保存编辑结果（[ChecklistItemEdit.id] 为 null = 新建）
 * @param onClearItemFields 清空可空字段（分类 / 截止时间 / 金额）
 * @param onAttachTag 挂标签
 * @param onDetachTag 摘标签
 * @param onAddSubTask 新增嵌套子清单项
 * @param onCategoryFilterChange 分类筛选
 * @param onTagFilterChange 标签筛选
 * @param onGroupChange 分组方式
 * @param onSortChange 排序方式
 * @param onResetFilter 重置筛选
 * @param selectedIds Phase 18.6：当前选中的清单项 id
 * @param onToggleSelect Phase 18.6：选中 / 取消选中某一项
 * @param onClearSelection Phase 18.6：退出多选
 * @param onBatchSetDueDate Phase 18.6：批量改截止时间（null = 清除）
 * @param onBatchSetPriority Phase 18.6：批量改优先级
 * @param onBatchMoveToList Phase 18.6：批量移入清单
 * @param onBatchAttachTag Phase 18.6：批量加标签
 * @param onBatchDelete Phase 18.6：批量删除
 */
@Composable
fun ChecklistTab(
    state: ChecklistUiState,
    onToggleComplete: (Long, Boolean) -> Unit,
    onDelete: (Task) -> Unit,
    onAdd: (String) -> Unit,
    onSaveItem: (ChecklistItemEdit) -> Unit,
    onClearItemFields: (Long, Boolean, Boolean, Boolean) -> Unit,
    onAttachTag: (Long, Long) -> Unit,
    onDetachTag: (Long, Long) -> Unit,
    onAddSubTask: (Long, String) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onTagFilterChange: (Long?) -> Unit,
    onGroupChange: (ChecklistGroup) -> Unit,
    onSortChange: (ChecklistSort) -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier,
    /** Phase 22.6：完成率统计（列表上方那张卡片）。 */
    completionStats: CompletionStats = CompletionStats(),
    // ===== Phase 18.6：多选 + 批量操作 =====
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Long) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onBatchSetDueDate: (Long?) -> Unit = {},
    onBatchSetPriority: (TaskPriority) -> Unit = {},
    onBatchMoveToList: (Long) -> Unit = {},
    onBatchAttachTag: (Long) -> Unit = {},
    onBatchDelete: () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    // ⚠️ 用 remember 而不是 rememberSaveable：ChecklistItemUi 不是 Parcelable/Serializable，
    // rememberSaveable 在存状态时会直接抛异常。
    var editingItem by remember { mutableStateOf<ChecklistItemUi?>(null) }
    var creatingItem by remember { mutableStateOf(false) }
    var expandedIds by remember { mutableStateOf(emptySet<Long>()) }

    // Phase 18.6：批量操作面板的开关
    var showDateSheet by remember { mutableStateOf(false) }
    var showPrioritySheet by remember { mutableStateOf(false) }
    var showListSheet by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val selectionMode = selectedIds.isNotEmpty()
    val canSubmit = input.isNotBlank()

    // ===== Phase 18.3：解析结果（chip 行画在输入框上方）=====
    // 纯函数 + 「今天」由外部传入 —— 每次输入变化重算一遍。
    val parsed = remember(input) {
        parseQuickInput(
            input = input,
            today = LocalDate.now(),
            zoneId = ZoneId.systemDefault(),
        )
    }

    // ===== Phase 18.4：拖拽排序 =====
    // 复用任务页那套拖拽状态机：拖拽过程中只动本地顺序，
    // 结束时一次性把新顺序写进 `tasks.manualOrder`（TaskRepository.reorderTasks）。
    val reorderVm: ReorderableTaskListViewModel = hiltViewModel(key = "reorder-checklist")
    val globalIds = state.allTopLevelIds
    LaunchedEffect(globalIds) { reorderVm.resetLocalOrder(globalIds) }
    val localOrder by reorderVm.localOrder.collectAsState()
    val lazyState = rememberLazyListState()

    // 正在被拖的那一项 + 它跟手的视觉偏移
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // 本次拖拽开始时的顺序（落库时用它判断「顺序到底变没变」）
    var dragSnapshot by remember { mutableStateOf<List<Long>>(emptyList()) }

    // 当前视图下的顶层项（可见、可拖的那些）
    val visibleItems = state.itemDetails
    val visibleIdSet = remember(visibleItems) { visibleItems.map { it.id }.toSet() }

    /**
     * 能不能拖：
     * - 手动排序（否则拖了也没意义）
     * - 不分组（分组后跨组换位没有语义）
     * - 没有筛选条件（筛出来的只是子集，动子集会打乱全局顺序）
     */
    val canReorder = !selectionMode &&
        state.filter.group == ChecklistGroup.NONE &&
        state.filter.sort == ChecklistSort.MANUAL &&
        state.filter.category == null &&
        state.filter.tagId == null &&
        state.filter.expression == null

    // 拖拽中 / 刚落库还没回写：一直按本地（全局）顺序显示 —— 否则会闪一下旧顺序
    val displayItems: List<ChecklistItemUi> = if (canReorder && localOrder.isNotEmpty()) {
        val byId = visibleItems.associateBy { it.id }
        localOrder.filter { it in visibleIdSet }.mapNotNull { byId[it] }
    } else {
        visibleItems
    }

    // 某个清单项在列表里的高度（像素）—— 用来判断「拖过半行就换位」
    val itemHeight: (Long) -> Int = { id ->
        lazyState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "item-$id" }?.size ?: 0
    }

    val onItemDragStart: (Long) -> Unit = { id ->
        draggingId = id
        dragOffsetY = 0f
        // 从当前本地顺序开始（上一轮拖完还没回写 DB 时，别把顺序弹回去）
        val current = reorderVm.localOrder.value
        val snapshot = if (current.isEmpty()) globalIds else current
        dragSnapshot = snapshot
        reorderVm.beginDrag(snapshot)
    }

    val onItemDrag: (Long, Float) -> Unit = { _, deltaY ->
        dragOffsetY += deltaY
        val dragged = draggingId
        if (dragged != null) {
            // 一次拖拽可能连着越过好几行 —— 用循环把能换的都换了
            var guard = 0
            while (guard++ < 32) {
                val order = reorderVm.localOrder.value
                val visible = order.filter { it in visibleIdSet }
                val index = visible.indexOf(dragged)
                if (index < 0) break
                // 向下
                val nextId = visible.getOrNull(index + 1)
                val nextHeight = nextId?.let { itemHeight(it) } ?: 0
                if (nextId != null && nextHeight > 0 && dragOffsetY > nextHeight / 2f) {
                    reorderVm.moveLocal(order.indexOf(dragged), order.indexOf(nextId))
                    // 布局跟着上移了一行 —— 补偿掉视觉偏移，被拖的项才不会跳一下
                    dragOffsetY -= nextHeight
                    continue
                }
                // 向上
                val prevId = visible.getOrNull(index - 1)
                val prevHeight = prevId?.let { itemHeight(it) } ?: 0
                if (prevId != null && prevHeight > 0 && dragOffsetY < -prevHeight / 2f) {
                    reorderVm.moveLocal(order.indexOf(dragged), order.indexOf(prevId))
                    dragOffsetY += prevHeight
                    continue
                }
                break
            }
        }
    }

    val onItemDragEnd: () -> Unit = {
        // 一次性落库（manualOrder）；顺序没变的话 VM 会跳过这次写入
        reorderVm.endDrag(dragSnapshot)
        draggingId = null
        dragOffsetY = 0f
    }

    // 生效的筛选条件条数 —— 徽标上显示的数字
    // 直接数四个布尔值，省掉每次重组都要新建的临时 List
    val activeFilterCount =
        (if (state.filter.category != null) 1 else 0) +
            (if (state.filter.tagId != null) 1 else 0) +
            (if (state.filter.group != ChecklistGroup.NONE) 1 else 0) +
            (if (state.filter.sort != ChecklistSort.MANUAL) 1 else 0)

    // Phase 10 修复（沿用）：把提交逻辑抽出来，IconButton 点击 + 软键盘「完成」共用同一条路径，
    // 保证两条入口行为一致（trim 校验、清空输入框、调 onAdd）。
    val submit: () -> Unit = {
        val title = input.trim()
        if (title.isNotEmpty()) {
            onAdd(title)
            input = ""
        }
    }

    // 保存编辑结果：
    // - id == null → 新建（onSaveItem 内部走 addItemWithDetails）
    // - id != null → 更新；**清空**可空字段必须走 onClearItemFields
    //   （数据层 updateItem 里 null 表示「不改」，所以「清空」是另一条路）
    val saveItem: (ChecklistItemEdit) -> Unit = { edit ->
        val original = editingItem
        val id = edit.id
        if (id != null) {
            onSaveItem(edit)
            // 清空语义：只有「原来是有的、现在要变成没有」才需要 clearItemFields。
            // onClearItemFields 是函数类型的参数，不能用具名实参 —— 先算成局部变量再按位置传。
            val clearCategory = edit.category == null && original?.category != null
            val clearDueDate = edit.dueDate == null && original?.dueDate != null
            val clearAmount = edit.amount == null && original?.amount != null
            onClearItemFields(id, clearCategory, clearDueDate, clearAmount)
        } else {
            onSaveItem(edit)
        }
        editingItem = null
        creatingItem = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ===== 顶部：快速添加区 =====
        // Phase 18.3：解析结果的 chip 行画在**输入框上方**。
        // Phase 18.6：多选模式下整个快速添加区（含 chip 行）都隐藏 ——
        //              多选是用来批量操作已有项的，不该同时在新建。
        if (!selectionMode) {
            QuickInputChipRow(
                parsed = parsed,
                onRemove = { token ->
                    // 点掉 chip = 把这一段从输入文本里删掉（重新解析后 chip 就没了）
                    input = removeTokenRange(input, token.range)
                },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.checklist_input_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    // Phase 10 修复：让软键盘右下角变成「完成」，按回车真的能提交。
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submit() },
                    ),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { creatingItem = true }) {
                    Icon(
                        imageVector = Icons.Rounded.AddCircle,
                        contentDescription = stringResource(R.string.checklist_detail_add),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    // Phase 10 修复：保留 enabled 守卫避免提交空项，但 disabled 时显式
                    // 给 Icon 一个 outline 灰色并加透明度，让「+ 不可点」这件事一眼可见。
                    onClick = submit,
                    enabled = canSubmit,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.action_add),
                        tint = if (canSubmit) {
                            LocalContentColor.current
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        },
                    )
                }
                FilterEntryButton(
                    activeCount = activeFilterCount,
                    contentDescription = stringResource(R.string.checklist_filter_button),
                    onClick = { showFilterSheet = true },
                )
            }
        } else {
            // ===== Phase 18.6：多选模式下的批量操作条 =====
            ChecklistBatchBar(
                selectedCount = selectedIds.size,
                onChangeDate = { showDateSheet = true },
                onChangePriority = { showPrioritySheet = true },
                onMoveToList = { showListSheet = true },
                onAttachTag = { showTagSheet = true },
                onDelete = { showDeleteDialog = true },
                onCancel = onClearSelection,
            )
        }

        HorizontalDivider()

        // ===== 列表 =====
        when {
            state.isLoading -> ChecklistListSkeleton()

            state.itemDetails.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        illustration = R.drawable.ic_empty_checklist,
                        title = stringResource(
                            if (activeFilterCount > 0) {
                                R.string.empty_checklist_filtered_title
                            } else {
                                R.string.empty_checklist_title
                            }
                        ),
                        description = stringResource(
                            if (activeFilterCount > 0) {
                                R.string.empty_checklist_filtered_desc
                            } else {
                                R.string.empty_checklist_desc
                            }
                        ),
                    )
                }
            }

            else -> {
                LazyColumn(state = lazyState, modifier = Modifier.fillMaxSize()) {
                    // Phase 22.6：完成率卡片（列表最上方）
                    if (!completionStats.isEmpty) {
                        item(key = "completion_stats") {
                            CompletionStatsCard(stats = completionStats)
                        }
                    }
                    if (canReorder) {
                        // 单组 + 手动排序：整段可以拖拽排序
                        items(displayItems, key = { "item-${it.id}" }) { item ->
                            ChecklistListItemCard(
                                item = item,
                                expandedIds = expandedIds,
                                onToggleExpand = { id ->
                                    expandedIds = if (expandedIds.contains(id)) {
                                        expandedIds - id
                                    } else {
                                        expandedIds + id
                                    }
                                },
                                onToggleComplete = onToggleComplete,
                                onDelete = { onDelete(it.toSubTask()) },
                                onEdit = { editingItem = it },
                                onAddSubTask = { parentId, title -> onAddSubTask(parentId, title) },
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                onToggleSelect = { id -> onToggleSelect(id) },
                                dragEnabled = true,
                                isDragging = draggingId == item.id,
                                modifier = if (draggingId == item.id) {
                                    // Phase 18.4：被拖的项跟着手指走
                                    Modifier.offset { IntOffset(0, dragOffsetY.roundToInt()) }
                                } else {
                                    Modifier
                                },
                                onDragStart = { onItemDragStart(item.id) },
                                onDrag = { delta -> onItemDrag(item.id, delta) },
                                onDragEnd = onItemDragEnd,
                            )
                        }
                    } else {
                        state.sections.forEach { section ->
                            if (section.items.isEmpty()) return@forEach
                            // 不分组时只有一个「全部」section，标题是废话 —— 不画。
                            if (state.filter.group != ChecklistGroup.NONE) {
                                item(key = "section-${section.key}") {
                                    ChecklistSectionHeader(
                                        title = sectionTitle(section),
                                        count = section.items.size,
                                    )
                                }
                            }
                            items(
                                items = section.items,
                                key = { "item-${it.id}" },
                            ) { item ->
                                ChecklistListItemCard(
                                    item = item,
                                    expandedIds = expandedIds,
                                    onToggleExpand = { id ->
                                        expandedIds = if (expandedIds.contains(id)) {
                                            expandedIds - id
                                        } else {
                                            expandedIds + id
                                        }
                                    },
                                    onToggleComplete = onToggleComplete,
                                    onDelete = { onDelete(it.toSubTask()) },
                                    onEdit = { editingItem = it },
                                    onAddSubTask = { parentId, title -> onAddSubTask(parentId, title) },
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                    onToggleSelect = { onToggleSelect(item.id) },
                                    dragEnabled = false,
                                    isDragging = false,
                                    modifier = Modifier,
                                    onDragStart = {},
                                    onDrag = {},
                                    onDragEnd = {},
                                )
                            }
                        }
                    }
                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // ===== 筛选面板 =====
    if (showFilterSheet) {
        ChecklistFilterSheet(
            filter = state.filter,
            availableTags = state.availableTags,
            onCategoryFilterChange = onCategoryFilterChange,
            onTagFilterChange = onTagFilterChange,
            onGroupChange = onGroupChange,
            onSortChange = onSortChange,
            onReset = onResetFilter,
            onDismiss = { showFilterSheet = false },
        )
    }

    // ===== Phase 18.6：批量操作面板 =====
    if (showDateSheet) {
        ChecklistBatchDateSheet(
            onPick = { dueDate ->
                showDateSheet = false
                onBatchSetDueDate(dueDate)
            },
            onDismiss = { showDateSheet = false },
        )
    }
    if (showPrioritySheet) {
        ChecklistBatchPrioritySheet(
            onPick = { priority ->
                showPrioritySheet = false
                onBatchSetPriority(priority)
            },
            onDismiss = { showPrioritySheet = false },
        )
    }
    if (showListSheet) {
        ChecklistBatchListSheet(
            lists = state.lists,
            onPick = { listId ->
                showListSheet = false
                onBatchMoveToList(listId)
            },
            onDismiss = { showListSheet = false },
        )
    }
    if (showTagSheet) {
        ChecklistBatchTagSheet(
            tags = state.availableTags,
            onPick = { tagId ->
                showTagSheet = false
                onBatchAttachTag(tagId)
            },
            onDismiss = { showTagSheet = false },
        )
    }
    if (showDeleteDialog) {
        ChecklistBatchDeleteDialog(
            count = selectedIds.size,
            onConfirm = {
                showDeleteDialog = false
                onBatchDelete()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }

    // ===== 编辑 / 新建弹窗 =====
    val currentEdit = editingItem
    if (currentEdit != null || creatingItem) {
        ChecklistItemEditSheet(
            item = currentEdit,
            availableTags = state.availableTags,
            onDismiss = {
                editingItem = null
                creatingItem = false
            },
            onSave = saveItem,
            onDelete = currentEdit?.let { target ->
                {
                    onDelete(target.toSubTask())
                    editingItem = null
                    creatingItem = false
                }
            },
            onAttachTag = { tagId -> currentEdit?.let { onAttachTag(it.id, tagId) } },
            onDetachTag = { tagId -> currentEdit?.let { onDetachTag(it.id, tagId) } },
        )
    }
}

/**
 * 列表里的清单项卡片（两条渲染路径共用一份画法）。
 *
 * @param modifier 列表项的外层修饰符（拖拽时在这里挂跟手的偏移）
 */
@Composable
private fun ChecklistListItemCard(
    item: ChecklistItemUi,
    expandedIds: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    onToggleComplete: (Long, Boolean) -> Unit,
    onDelete: (ChecklistItemUi) -> Unit,
    onEdit: (ChecklistItemUi) -> Unit,
    onAddSubTask: (Long, String) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    dragEnabled: Boolean,
    isDragging: Boolean,
    modifier: Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    ChecklistItemCard(
        item = item,
        expandedIds = expandedIds,
        onToggleExpand = onToggleExpand,
        onToggleComplete = onToggleComplete,
        onDelete = onDelete,
        onEdit = onEdit,
        onAddChild = { parentId, title -> onAddSubTask(parentId, title) },
        modifier = modifier,
        // ===== Phase 18.6：多选 =====
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        onToggleSelect = onToggleSelect,
        // ===== Phase 18.4：拖拽排序 =====
        dragEnabled = dragEnabled,
        isDragging = isDragging,
        onDragStart = onDragStart,
        onDrag = onDrag,
        onDragEnd = onDragEnd,
    )
}

/**
 * 分组标题 —— 固定 key 的分组用 strings.xml 里的文案，
 * 「按分类」分组的标题就是分类名（数据层给的），直接用。
 */
@Composable
private fun sectionTitle(section: ChecklistSectionUi): String {
    val res = when (section.key) {
        "all" -> R.string.checklist_group_all
        "uncompleted" -> R.string.checklist_group_uncompleted
        "completed" -> R.string.checklist_group_completed
        "overdue" -> R.string.checklist_group_overdue
        "today" -> R.string.checklist_group_today
        "later" -> R.string.checklist_group_later
        "noDueDate" -> R.string.checklist_group_no_due_date
        else -> null
    }
    return if (res != null) stringResource(res) else section.title
}

/** 分组标题行（标题 + 条数 + 分隔线）。 */
@Composable
private fun ChecklistSectionHeader(title: String, count: Int) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        HorizontalDivider()
    }
}

/** 加载中的骨架屏。 */
@Composable
private fun ChecklistListSkeleton() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(AppRadii.card))
                    .background(placeholderColor),
            )
        }
    }
}

/**
 * 清单项 UI 模型 → Domain 模型（删除用）。
 *
 * 仓库层的 `deleteItem` 最终是 Room 的 `@Delete`，**只按主键匹配**，
 * 所以这里只需要 id 正确；其余字段填上是为了对象本身自洽。
 */
internal fun ChecklistItemUi.toSubTask(): Task = Task(
    id = id,
    // Phase 16：嵌套层级统一用 `parentTaskId` 表达（原 `parentSubTaskId` 已并入 `tasks` 表）
    parentTaskId = parentSubTaskId,
    title = title,
    isCompleted = isCompleted,
    manualOrder = order.toLong(),
    createdAt = 0L,
    category = category,
    dueDate = dueDate,
    priority = priority,
    amount = amount,
)
