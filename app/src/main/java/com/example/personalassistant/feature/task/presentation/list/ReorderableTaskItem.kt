package com.example.personalassistant.feature.task.presentation.list

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.presentation.components.TaskItem
import kotlin.math.roundToInt

/**
 * Phase 6a：可拖拽条目（核心实现）
 *
 * - 长按 + 拖动触发（[detectDragGesturesAfterLongPress]）
 * - 拖拽期间通过 [onDrag] 回调 y 增量 / [onDragStart] / [onDragEnd] 边界回调
 * - [isDragging] 为 true 时浮起阴影（6dp）
 * - 拖拽中由调用方传入 [translationY] 让当前 Item 视觉跟随手指
 *
 * 调用方 [ReorderableTaskList] 负责：
 * - 跟踪每个 Item 的**行高**（从 `LazyListState.layoutInfo` 查）
 * - 根据拖动偏移计算命中行索引并调 [onDrag] 通知 VM
 *
 * ## Phase 24：不再上报「中心 Y」
 *
 * 老实现用 `pointerInput` 的 `size.height / 2` 当作「中心 Y」传给调用方 ——
 * 那是**项自身高度的一半**，不是视口坐标。于是所有行的命中窗口完全重叠，
 * 命中判定退化成「位移超过一整行高度才换位」。
 * 现在由调用方从 `LazyListState.layoutInfo` 取真实行高，判定规则见 [ReorderHitResolver]。
 *
 * 该组件**不**关心跨象限 / 多象限排序 —— 由调用方控制范围（同一 LazyColumn 容器内）。
 */
@Composable
fun ReorderableTaskItem(
    task: Task,
    isDragging: Boolean,
    onToggleComplete: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
    translationY: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val elevation = if (isDragging) 6.dp else 0.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation)
            .offset { IntOffset(0, translationY.roundToPx()) }
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                )
            },
    ) {
        TaskItem(
            task = task,
            onToggleComplete = onToggleComplete,
            onClick = onClick,
            onDelete = onDelete,
        )
    }
}
