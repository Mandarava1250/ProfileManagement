package com.example.personalassistant.feature.task.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.personalassistant.feature.task.domain.model.Task

/**
 * Phase 6a：可拖拽任务列表（单 LazyColumn 内的同象限）。
 *
 * 工作流程：
 * 1. 接收 [globalOrder]（**全局** id 顺序，落库用）与 [snapshot]（本象限的任务，显示用）
 * 2. 拖拽时按 [ReorderableTaskListViewModel.localOrder] 重排显示
 * 3. 拖拽结束一次性 [ReorderableTaskListViewModel.endDrag] 提交到 DB
 *
 * ## Phase 24：两处修正
 *
 * ### 1. 命中判定改用真实行高
 *
 * 老实现让 [ReorderableTaskItem] 上报「中心 Y」，但它传的是**项自身高度的一半**，
 * 不是视口坐标 —— 所有行的命中窗口完全重叠，
 * 判定退化成「位移超过一整行高度才换位」。
 * 现在直接从 `LazyListState.layoutInfo` 按 key 查目标行的**行高**，
 * 跟累计位移比**半行高**（规则见 [ReorderHitResolver]）——
 * 与清单侧（ChecklistTab）的做法完全同构。
 *
 * ### 2. manualOrder 写全局下标
 *
 * 老实现落库时传的是**象限内**的 id 列表，`reorderTasks` 拿列表下标当 manualOrder，
 * 于是两个象限的第 0 项拿到同一个 manualOrder（撞值）。
 * 现在拖拽中换位仍然只在**本象限内**进行（KDoc 既定规矩），
 * 但落库写的是**全局**顺序 —— `manualOrder = 0..N-1` 全局唯一。
 *
 * 设计取舍：
 * - 拖拽仅在「sort = MANUAL_ORDER」时启用（由调用方控制，本组件只管拖拽 UI）
 * - 跨象限禁止：调用方按象限分组，每个象限调用本组件一次
 *
 * @param scopeKey 象限标识（每个象限一份独立的 VM 实例）
 * @param snapshot 本象限的任务（显示 + 命中判定范围）
 * @param globalOrder 全局 id 顺序（**不按象限过滤**）—— 落库时的 manualOrder 就按它写
 */
@Composable
fun ReorderableTaskList(
    scopeKey: String,
    snapshot: List<Task>,
    globalOrder: List<Long>,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClick: (Long) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReorderableTaskListViewModel = hiltViewModel(key = "reorder-$scopeKey"),
) {
    // 初始化 / DB 更新时同步本地顺序（全局）
    LaunchedEffect(globalOrder) {
        viewModel.resetLocalOrder(globalOrder)
    }
    LaunchedEffect(scopeKey) {
        viewModel.scopeKey = scopeKey
    }

    val localOrder by viewModel.localOrder.collectAsState()
    val isDragging by viewModel.isDragging.collectAsState()

    // 正在被拖的那一项 + 它跟手的视觉偏移
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // 本次拖拽开始时的全局顺序（落库时用它判断「顺序到底变没变」）
    var dragSnapshot by remember { mutableStateOf<List<Long>>(emptyList()) }

    // 本象限的 id 集合 + id → 任务
    val scopeIdSet = remember(snapshot) { snapshot.map { it.id }.toSet() }
    val byId = remember(snapshot) { snapshot.associateBy { it.id } }

    // 本象限的可见顺序：拖拽中按全局本地顺序排，没拖拽时按传进来的顺序
    val visibleTasks: List<Task> = if (isDragging) {
        localOrder.filter { it in scopeIdSet }.mapNotNull { byId[it] }
    } else {
        snapshot
    }

    val lazyState = rememberLazyListState()
    val density = LocalDensity.current

    // 某个任务在列表里的高度（像素）—— 用来判断「拖过半行就换位」
    val itemHeight: (Long) -> Int = { id ->
        lazyState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.size ?: 0
    }

    val onItemDragStart: (Long) -> Unit = { id ->
        draggingId = id
        dragOffsetY = 0f
        // 从当前本地顺序开始（上一轮拖完还没回写 DB 时，别把顺序弹回去）
        val current = viewModel.localOrder.value
        val snapshotOrder = if (current.isEmpty()) globalOrder else current
        dragSnapshot = snapshotOrder
        viewModel.beginDrag(snapshotOrder)
    }

    val onItemDrag: (Long, Float) -> Unit = { _, deltaY ->
        dragOffsetY += deltaY
        val dragged = draggingId
        if (dragged != null) {
            // 一次拖拽可能连着越过好几行 —— 用循环把能换的都换了
            var guard = 0
            while (guard++ < 32) {
                val order = viewModel.localOrder.value
                // 只在本象限内换位（跨象限拖拽没有语义）
                val visible = order.filter { it in scopeIdSet }
                val index = visible.indexOf(dragged)
                if (index < 0) break
                // 向下
                val nextId = visible.getOrNull(index + 1)
                val nextHeight = nextId?.let { itemHeight(it) } ?: 0
                if (nextId != null && dragOffsetY > 0f &&
                    ReorderHitResolver.shouldSwap(dragOffsetY, nextHeight)
                ) {
                    viewModel.moveLocal(order.indexOf(dragged), order.indexOf(nextId))
                    // 布局跟着上移了一行 —— 补偿掉视觉偏移，被拖的项才不会跳一下
                    dragOffsetY -= nextHeight
                    continue
                }
                // 向上
                val prevId = visible.getOrNull(index - 1)
                val prevHeight = prevId?.let { itemHeight(it) } ?: 0
                if (prevId != null && dragOffsetY < 0f &&
                    ReorderHitResolver.shouldSwap(dragOffsetY, prevHeight)
                ) {
                    viewModel.moveLocal(order.indexOf(dragged), order.indexOf(prevId))
                    dragOffsetY += prevHeight
                    continue
                }
                break
            }
        }
    }

    val onItemDragEnd: () -> Unit = {
        // 一次性落库（manualOrder = 全局下标）；顺序没变的话 VM 会跳过这次写入
        viewModel.endDrag(dragSnapshot)
        draggingId = null
        dragOffsetY = 0f
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = lazyState,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(visibleTasks, key = { it.id }) { task ->
            // 当前 Item 是否正在被拖动
            val isThisDragging = isDragging && draggingId == task.id
            // 拖动时跟随手指的视觉偏移（仅当前拖动 Item 自身）
            val translationY = if (isThisDragging) {
                with(density) { dragOffsetY.toDp() }
            } else 0.dp

            ReorderableTaskItem(
                task = task,
                isDragging = isThisDragging,
                onToggleComplete = { onToggleComplete(task.id, it) },
                onClick = { onClick(task.id) },
                onDelete = { onDelete(task) },
                onDragStart = { onItemDragStart(task.id) },
                onDrag = { delta -> onItemDrag(task.id, delta) },
                onDragEnd = onItemDragEnd,
                translationY = translationY,
            )
        }
    }
}
