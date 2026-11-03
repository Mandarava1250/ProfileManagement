package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.personalassistant.R
import com.example.personalassistant.feature.task.presentation.ChecklistItemUi

/**
 * 清单页的清单项卡片（Phase 15）—— 带分类 / 标签 / 截止 / 金额 / 优先级 + 可展开的子清单。
 *
 * 与旧的 [ChecklistItem] 的关系：
 * - 旧的 [ChecklistItem] 吃 [com.example.personalassistant.feature.task.domain.model.Task]，
 *   服务于任务详情页 / 编辑页的子清单，那里拿不到标签与嵌套子项。
 * - 这里的 [ChecklistItemUi] 是清单页专用的 UI 模型，自带 tags / children。
 * - 两边共用同一套行画法 [ChecklistItemRow]，视觉不会分叉。
 *
 * ## 手势（Phase 18.4 / 18.6）
 *
 * 卡片的行外面套了一层 [ChecklistItemGestureBox]（长按 → 多选 / 长按竖拖 → 排序），
 * 行里面是 [SwipeToDeleteBox]（左滑 → 删除）。
 * 两层的分工见 [ChecklistItemGestureBox] 的注释：横滑删除先拿到事件，
 * 竖拖排序不会被它抢走。
 *
 * @param item 清单项（UI 模型）
 * @param expandedIds 当前展开的清单项 id 集合（由列表页统一持有，滚动不丢状态）
 * @param onToggleExpand 展开 / 收起某个清单项的子清单
 * @param onToggleComplete 勾选回调
 * @param onDelete 删除回调
 * @param onEdit 编辑回调（点编辑图标走它）
 * @param onAddChild 在该项下面加子项（null = 不显示子项输入框）
 * @param depth 当前嵌套层级（用于缩进）
 * @param selectionMode Phase 18.6：多选模式（勾选框变成「选中」框）
 * @param selectedIds Phase 18.6：当前被选中的清单项 id 集合（父子项共用一份）
 * @param onToggleSelect Phase 18.6：选中 / 取消选中（回调带 id —— 父子项各是各的）
 * @param dragEnabled Phase 18.4：是否允许拖拽排序（只有顶层项可以）
 * @param isDragging Phase 18.4：该项正被拖着
 * @param onDragStart / onDrag / onDragEnd Phase 18.4：拖拽手势回调
 */
@Composable
fun ChecklistItemCard(
    item: ChecklistItemUi,
    expandedIds: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    onToggleComplete: (Long, Boolean) -> Unit,
    onDelete: (ChecklistItemUi) -> Unit,
    onEdit: (ChecklistItemUi) -> Unit,
    onAddChild: ((Long, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    // ===== Phase 18.6：多选 =====
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: ((Long) -> Unit)? = null,
    // ===== Phase 18.4：拖拽排序 =====
    dragEnabled: Boolean = false,
    isDragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    val expanded = expandedIds.contains(item.id)
    val childCount = item.children.size
    val completedChildCount = item.children.count { it.isCompleted }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Phase 18.4：被拖起来的那一项浮起来一点（阴影）
            .shadow(if (isDragging) 6.dp else 0.dp),
    ) {
        // Phase 18.4 / 18.6：手势仲裁层（长按多选 / 长按竖拖排序）
        ChecklistItemGestureBox(
            enableLongPress = onToggleSelect != null,
            enableDrag = dragEnabled,
            onLongPress = { onToggleSelect?.invoke(item.id) },
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        ) {
            // Phase 18.5：左滑露出删除按钮（多选模式下关掉，避免误删）
            SwipeToDeleteBox(
                onDelete = { onDelete(item) },
                enabled = !selectionMode,
            ) {
                ChecklistItemRow(
                    title = item.title,
                    isCompleted = item.isCompleted,
                    onToggleComplete = { onToggleComplete(item.id, it) },
                    onDelete = { onDelete(item) },
                    category = item.category,
                    dueDate = item.dueDate,
                    priority = item.priority,
                    amount = item.amount,
                    tags = item.tags,
                    onEdit = { onEdit(item) },
                    // 长按已经交给手势仲裁层做「多选」，这里不再挂长按手势
                    onLongPress = null,
                    gestureKey = item.id,
                    trailingContent = if (childCount > 0) {
                        {
                            ChildrenHeader(
                                completedCount = completedChildCount,
                                totalCount = childCount,
                                expanded = expanded,
                                onToggle = { onToggleExpand(item.id) },
                            )
                        }
                    } else {
                        null
                    },
                    // ===== Phase 18.6：多选 =====
                    selectionMode = selectionMode,
                    selected = item.id in selectedIds,
                    onToggleSelect = { onToggleSelect?.invoke(item.id) },
                )
            }
        }

        if (expanded && childCount > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp * (depth + 1), top = 2.dp),
            ) {
                item.children.forEach { child ->
                    ChecklistItemCard(
                        item = child,
                        expandedIds = expandedIds,
                        onToggleExpand = onToggleExpand,
                        onToggleComplete = onToggleComplete,
                        onDelete = onDelete,
                        onEdit = onEdit,
                        // 子项不再允许再挂子项 —— 数据层最多 3 层，
                        // 再往下加会让树深到看不下去
                        onAddChild = null,
                        depth = depth + 1,
                        // ===== Phase 18.6：子项也能被选中 =====
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelect = onToggleSelect,
                        // Phase 18.4：只有顶层项能拖拽排序
                        dragEnabled = false,
                    )
                }
                if (onAddChild != null) {
                    AddChildInput(onAdd = { title -> onAddChild(item.id, title) })
                }
            }
        }
    }
}

/**
 * 子清单的折叠头 —— 展开箭头 + 完成进度徽标（如 2/5）。
 *
 * 进度徽标的画法参考任务详情页的 `SubTaskSection`（`完成数/总数` + 次级文字色）。
 */
@Composable
private fun ChildrenHeader(
    completedCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = stringResource(
                if (expanded) R.string.checklist_item_collapse else R.string.checklist_item_expand
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.checklist_progress, completedCount, totalCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 子项输入框（展开子清单后可以就地加一条子项）。 */
@Composable
private fun AddChildInput(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.checklist_add_sub_item_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = {
                val title = text.trim()
                if (title.isNotEmpty()) {
                    onAdd(title)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.action_add),
                tint = if (text.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}
