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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.EmptyState
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.feature.task.domain.model.ChecklistGroup
import com.example.personalassistant.feature.task.domain.model.ChecklistSort
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.presentation.components.ChecklistItemCard
import com.example.personalassistant.feature.task.presentation.components.FilterEntryButton

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
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    // ⚠️ 用 remember 而不是 rememberSaveable：ChecklistItemUi 不是 Parcelable/Serializable，
    // rememberSaveable 在存状态时会直接抛异常。
    var editingItem by remember { mutableStateOf<ChecklistItemUi?>(null) }
    var creatingItem by remember { mutableStateOf(false) }
    var expandedIds by remember { mutableStateOf(emptySet<Long>()) }

    val canSubmit = input.isNotBlank()

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
        // ===== 顶部：输入框 + 添加 + 筛选入口 =====
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                            ChecklistItemCard(
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
                                onAddChild = { parentId, title -> onAddSubTask(parentId, title) },
                            )
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
