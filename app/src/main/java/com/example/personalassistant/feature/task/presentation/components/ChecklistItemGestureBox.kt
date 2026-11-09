package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

/**
 * Phase 18.4 / 18.6：清单项的**手势仲裁层**。
 *
 * 一个清单项上同时挂着三种手势，谁先「赢」由这里裁决：
 *
 * 1. **横滑操作**（左滑 / 右滑）：交给内层 [SwipeActionBox]。
 *    它一旦吃掉位移（consume），本层立刻放弃 —— 不会出现「滑到一半又变拖拽」。
 * 2. **长按 → 多选**（Phase 18.6）：按住不动直到长按超时，然后抬手 → 进入多选。
 * 3. **长按 → 竖拖 → 排序**（Phase 18.4）：长按超时后**继续移动**超过触摸阈值 → 转为拖拽排序。
 *
 * ## 为什么自己写而不用 `detectTapGestures` + `detectDragGesturesAfterLongPress`
 *
 * 那两个 API 都会在**同一个长按超时**上触发，分不清「长按多选」和「长按拖拽」——
 * 用户想拖拽排序，结果先被选中了一半。
 *
 * 这里用一个手势循环把两种意图分开：**抬手 = 多选，移动 = 拖拽**。
 * 顺序也很重要：本层挂在 [SwipeActionBox] **外面**，所以横滑先拿到事件、
 * 先 consume，本层看到「已被消费」就退出 —— 两边不会互相抢。
 *
 * @param enableLongPress 是否启用「长按 → 多选」
 * @param enableDrag 是否启用「长按 → 竖拖 → 排序」
 * @param onLongPress 长按成立（抬手且没拖动）时回调
 * @param onDragStart 认定是拖拽时回调（此刻开始跟手）
 * @param onDrag 拖拽过程中的 Y 位移增量（像素）
 * @param onDragEnd 拖拽结束（抬手或手势被取消）
 */
@Composable
internal fun ChecklistItemGestureBox(
    enableLongPress: Boolean = true,
    enableDrag: Boolean = true,
    onLongPress: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (deltaY: Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 回调用 rememberUpdatedState 兜住：手势只在 key 变化时重启，
    // 但回调必须永远拿到最新的一份，否则会调到旧数据。
    val currentLongPress by rememberUpdatedState(onLongPress)
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = modifier.pointerInput(enableLongPress, enableDrag) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                val touchSlop = viewConfiguration.touchSlop

                // ===== 阶段一：等长按 =====
                // 期间若有人吃掉位移（横滑删除 / 列表滚动），立刻放弃这一轮手势。
                var aborted = false
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.any { it.isConsumed }) {
                                aborted = true
                                break
                            }
                            if (event.changes.any { it.id == pointerId && it.changedToUpIgnoreConsumed() }) {
                                aborted = true
                                break
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    // 超时 = 长按成立（下面继续）
                }
                if (aborted || !enableLongPress) return@awaitEachGesture

                // ===== 阶段二：长按已成立 —— 抬手 = 多选，移动 = 拖拽 =====
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: continue

                    if (change.changedToUpIgnoreConsumed()) {
                        if (dragging) {
                            currentDragEnd()
                        } else {
                            currentLongPress()
                        }
                        break
                    }

                    // 被别的处理器吃掉（列表滚动 / 横滑删除）→ 这一轮手势结束
                    if (change.isConsumed) {
                        if (dragging) currentDragEnd()
                        break
                    }

                    val delta = change.positionChange()
                    if (delta.x == 0f && delta.y == 0f) continue

                    if (!dragging) {
                        // 手指抖动不算拖拽 —— 要真的挪动超过触摸阈值才行
                        val moved = abs(change.position.y - down.position.y)
                        if (moved < touchSlop || !enableDrag) continue
                        dragging = true
                        currentDragStart()
                    }

                    // 吃掉位移：否则 LazyColumn 会跟着一起滚，拖拽排序就无从谈起
                    change.consume()
                    currentDrag(delta.y)
                }
            }
        },
    ) {
        content()
    }
}
