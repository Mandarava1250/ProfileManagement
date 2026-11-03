package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Phase 18.5：左滑删除
 *
 * 滴答的手感：**左滑露出删除按钮**，滑过一半就触发，滑回去就弹回。
 *
 * ## 为什么自己写而不用 `SwipeToDismissBox`
 *
 * Material3 的 `SwipeToDismissBox` 在不同版本里参数名改过好几轮
 * （`state` / `enableDismissFromStartToEnd` / `backgroundContent`），锁版本很脆。
 * 自己写就只依赖 `detectHorizontalDragGestures` + `Animatable` —— 这两个 API 一直很稳。
 *
 * ## 与拖拽排序（Phase 18.4）的手势分工
 *
 * 本组件只吃**横向**位移；竖向位移它不消费（`awaitHorizontalTouchSlopOrCancellation`
 * 在竖向超阈值时会放弃），所以竖拖排序不会被它抢走。
 * 反过来，本组件一旦消费了位移，外层的手势仲裁层（[ChecklistItemGestureBox]）
 * 就会看到「已被消费」而退出 —— 横滑删除与竖拖排序不会同时发生。
 *
 * @param enabled 是否启用左滑（多选模式下关掉，避免误删）
 */
@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 滑过这个距离就触发删除（跟删除按钮的宽度对齐）
    val deleteThreshold = 96.dp
    val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        deleteThreshold.toPx()
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 底层：删除区（跟着内容一起露出来）
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 24.dp),
            )
        }

        // 上层：可滑动的内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(offsetX.value) >= thresholdPx) {
                                    // 触发删除 —— 内容归位，条目本身从列表里消失
                                    offsetX.animateTo(0f)
                                    onDelete()
                                } else {
                                    // 没滑够 —— 弹回去
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                // 只允许往左滑（右滑没有对应的操作，先不开放）
                                val next = (offsetX.value + dragAmount).coerceIn(-thresholdPx * 1.5f, 0f)
                                offsetX.snapTo(next)
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
