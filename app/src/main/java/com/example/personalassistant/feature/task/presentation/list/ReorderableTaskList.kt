package com.example.personalassistant.feature.task.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.personalassistant.feature.task.domain.model.Task

/**
 * Phase 6a：可拖拽任务列表（单 LazyColumn 内的同象限）。
 *
 * 工作流程：
 * 1. 接收 DB 流式 [snapshot]（已按 [TaskSort.MANUAL_ORDER] 排好的任务）
 * 2. 拖拽时按 [ReorderableTaskListViewModel.localOrder] 重排显示
 * 3. 拖拽结束一次性 [ReorderableTaskListViewModel.endDrag] 提交到 DB
 *
 * 设计取舍：
 * - 拖拽仅在「sort = MANUAL_ORDER」时启用（由调用方控制，本组件只管拖拽 UI）
 * - 跨象限禁止：调用方按象限分组，每个象限调用本组件一次
 * - 用 `pointerInput` 子 modifier 透传位置信息（不绑额外状态变量）
 */
@Composable
fun ReorderableTaskList(
    scopeKey: String,
    snapshot: List<Task>,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClick: (Long) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReorderableTaskListViewModel = hiltViewModel(key = "reorder-$scopeKey"),
) {
    // 初始化 / DB 更新时同步本地顺序
    LaunchedEffect(snapshot) {
        viewModel.resetLocalOrder(snapshot.map { it.id })
    }
    LaunchedEffect(scopeKey) {
        viewModel.scopeKey = scopeKey
    }

    val localOrder by viewModel.localOrder.collectAsState()
    val isDragging by viewModel.isDragging.collectAsState()

    // 当前拖动 Item 的索引 / 累积 Y 偏移
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val lazyState = rememberLazyListState()

    // Item 中心 Y 跟踪表（id -> (centerY, height)）
    val itemBounds = remember { mutableStateMapOf<Long, Pair<Float, Float>>() }

    // 拖动期间按 localOrder 重排；非拖动按 snapshot
    val visibleTasks: List<Task> = if (isDragging) {
        val byId = snapshot.associateBy { it.id }
        localOrder.mapNotNull { byId[it] }
    } else {
        snapshot
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = lazyState,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(visibleTasks, key = { _, t -> t.id }) { index, task ->
            // 命中检测 + 重排回调
            val onItemDragStart = {
                val orderedIds = visibleTasks.map { it.id }
                viewModel.beginDrag(orderedIds)
                draggingIndex = index
                dragAccumulatedY = 0f
            }
            val onItemDrag: (Float) -> Unit = handler@{ deltaY ->
                dragAccumulatedY += deltaY
                val originalIndex = draggingIndex
                if (originalIndex < 0) return@handler
                val bounds = itemBounds[visibleTasks[originalIndex].id] ?: return@handler
                // 当前拖动 Item 的中心 Y 实际位置 = 起始 centerY + dragAccumulatedY
                val currentCenterY = bounds.first + dragAccumulatedY
                // 命中哪一行
                var newIndex = originalIndex
                for (i in visibleTasks.indices) {
                    if (i == originalIndex) continue
                    val target = itemBounds[visibleTasks[i].id] ?: continue
                    if (currentCenterY in (target.first - target.second / 2f)..(target.first + target.second / 2f)) {
                        newIndex = i
                        break
                    }
                }
                if (newIndex != originalIndex) {
                    viewModel.moveLocal(originalIndex, newIndex)
                    draggingIndex = newIndex
                    // 校正累积偏移：移动后拖动 Item 的 centerY 基准已变
                    val newBounds = itemBounds[visibleTasks[newIndex].id]
                    if (newBounds != null) {
                        dragAccumulatedY = currentCenterY - newBounds.first
                    }
                }
            }
            val onItemDragEnd = {
                viewModel.endDrag(snapshot.map { it.id })
                draggingIndex = -1
                dragAccumulatedY = 0f
            }

            // 当前 Item 是否正在被拖动
            val isThisDragging = isDragging && draggingIndex == index
            // 拖动时跟随手指的视觉偏移（仅当前拖动 Item 自身）
            val translationY = if (isThisDragging) {
                with(density) { dragAccumulatedY.toDp() }
            } else 0.dp

            ReorderableTaskItem(
                task = task,
                isDragging = isThisDragging,
                onToggleComplete = { onToggleComplete(task.id, it) },
                onClick = { onClick(task.id) },
                onDelete = { onDelete(task) },
                onDragStart = onItemDragStart,
                onDrag = onItemDrag,
                onDragEnd = onItemDragEnd,
                translationY = translationY,
                recordCenterY = { centerY, height ->
                    // 仅记录，未拖动时也要更新
                    itemBounds[task.id] = centerY to height
                },
            )
        }
    }
}
