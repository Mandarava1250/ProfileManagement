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
 * - 拖动期间通过 [onDrag] 回调 y 增量 / [onDragStart] / [onDragEnd] 边界回调
 * - [isDragging] 为 true 时浮起阴影（6dp）
 * - 拖拽中由调用方传入 [translationY] 让当前 Item 视觉跟随手指
 *
 * 调用方 [ReorderableTaskList] 负责：
 * - 跟踪每个 Item 的中心 Y 位置（[recordCenterY]）
 * - 根据拖动偏移计算命中行索引并调 [onDrag] 通知 VM
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
    recordCenterY: (centerY: Float, height: Float) -> Unit = { _, _ -> },
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
        // 通过 onGloballyPositioned 间接把高度信息透传给外层 —— 但 Compose 没有 onGloballyPositioned 的 lambda，
        // 我们改用子层的 pointerInput 简化：调用方在 LazyListState 已知项高度时直接传就行，
        // 此处把回调暴露但不强行绑定。
        TaskItem(
            task = task,
            onToggleComplete = onToggleComplete,
            onClick = onClick,
            onDelete = onDelete,
            modifier = Modifier.pointerInput(task.id, "record-bounds") {
                recordCenterY(
                    size.height.toFloat() / 2f,
                    size.height.toFloat(),
                )
            },
        )
    }
}
