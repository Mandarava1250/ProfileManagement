package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Phase 24：**通用双向滑动操作容器**。
 *
 * 滴答的手感：**滑动 → 露出动作图标 → 点图标才真的执行**。
 * 旧版的单动作删除容器是「滑过阈值直接执行」，只有一个动作时够用，
 * 多个动作时就会误伤（想改日期结果删了），所以这里改成「吸附停住，等用户点」。
 *
 * ## 结构
 *
 * 两层 Box（跟旧版一样的骨架）：
 *
 * - 底层：动作区。右滑动作靠**左**排、左滑动作靠**右**排。
 * - 上层：可滑动的内容。内容往右移 → 左边露出右滑动作；往左移 → 右边露出左滑动作。
 *
 * ## 吸附规则（左右各自独立算）
 *
 * - 每个动作按钮占 [ACTION_WIDTH]（64dp）。
 * - 该侧的吸附距离 = 该侧动作数 × 64dp（左滑 3 个 = 192dp，右滑 2 个 = 128dp）。
 * - 抬手时位移超过该侧吸附距离的**一半** → 吸附停住（露出全部图标，等用户点）；
 *   否则 → 弹回原位。
 *
 * ## 与手势仲裁层（[ChecklistItemGestureBox]）的分工
 *
 * 本组件只吃**横向**位移；竖向位移它不消费
 * （`awaitHorizontalTouchSlopOrCancellation` 在竖向超阈值时会放弃）。
 * 反过来，本组件一旦消费了位移，外层仲裁层就会看到「已被消费」而退出 ——
 * 横滑操作与长按多选 / 长按竖拖排序不会同时发生。
 *
 * ⚠️ **左右两个方向都必须 consume** —— 只消费左滑的话，右滑会漏给外层仲裁层，
 * 长按多选会被右滑误触发。
 *
 * ## 为什么不用 `SwipeToDismissBox`
 *
 * Material3 的 `SwipeToDismissBox` 在不同版本里参数名改过好几轮
 * （`state` / `enableDismissFromStartToEnd` / `backgroundContent`），锁版本很脆。
 * 自己写就只依赖 `detectHorizontalDragGestures` + `Animatable` —— 这两个 API 一直很稳。
 *
 * @param startActions 右滑露出的动作（靠左排）
 * @param endActions 左滑露出的动作（靠右排）
 * @param enabled 是否启用滑动（多选模式下关掉，避免误操作）
 */
@Composable
fun SwipeActionBox(
    startActions: List<SwipeAction> = emptyList(),
    endActions: List<SwipeAction> = emptyList(),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 内容层的底色。
     *
     * null = 用 `surface`。调用方要跟所在列表的底色一致时传自己的颜色。
     */
    contentBackground: Color? = null,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val density = LocalDensity.current
    val actionWidthPx = with(density) { ACTION_WIDTH.toPx() }
    // 左右各自的吸附距离 —— 动作数不同，距离也不同，不能共用一个阈值
    val startSnapPx = startActions.size * actionWidthPx
    val endSnapPx = endActions.size * actionWidthPx

    // 关掉手势时把内容归位（否则会停在半路，露出半个图标）
    LaunchedEffect(enabled) {
        if (!enabled) offsetX.snapTo(0f)
    }

    // 点完动作后把内容弹回原位
    val closeBox: () -> Unit = { scope.launch { offsetX.animateTo(0f) } }

    Box(modifier = modifier.fillMaxWidth()) {
        // 底层：动作区（右滑动作靠左排、左滑动作靠右排）
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.medium),
        ) {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 右滑动作组：靠左
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    startActions.forEach { action ->
                        SwipeActionButton(action = action, onDone = closeBox)
                    }
                }
                // 左滑动作组：靠右
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    endActions.forEach { action ->
                        SwipeActionButton(action = action, onDone = closeBox)
                    }
                }
            }
        }

        // 上层：可滑动的内容
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Phase 26：内容层必须**不透明** ——
                // 底层是动作区（彩色按钮），上层透明的话那几块颜色会整片透出来，
                // 把每一行切成紫/粉/橙的条纹。
                .background(contentBackground ?: MaterialTheme.colorScheme.surface)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(enabled, startSnapPx, endSnapPx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val current = offsetX.value
                                val target = when {
                                    // 右滑过半 → 吸附到右滑动作全露出的位置
                                    startSnapPx > 0f && current > startSnapPx / 2f -> startSnapPx
                                    // 左滑过半 → 吸附到左滑动作全露出的位置
                                    endSnapPx > 0f && current < -endSnapPx / 2f -> -endSnapPx
                                    // 没滑够 → 弹回原位
                                    else -> 0f
                                }
                                offsetX.animateTo(target)
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            // 左右都要 consume —— 否则右滑会漏给外层的手势仲裁层
                            change.consume()
                            scope.launch {
                                val next = (offsetX.value + dragAmount)
                                    .coerceIn(-endSnapPx, startSnapPx)
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

/**
 * Phase 24：一个滑动动作。
 *
 * @param icon 动作图标
 * @param label 无障碍标签（contentDescription）。
 *        ⚠️ 硬编码中文，不走 strings.xml —— 本批次不允许改 `res/`。
 * @param backgroundColor 动作按钮的背景色
 * @param contentColor 图标颜色
 * @param onClick 点击图标时执行（执行完容器会自动归位）
 */
data class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val backgroundColor: Color,
    val contentColor: Color = Color.White,
    val onClick: () -> Unit,
)

/** 单个动作按钮（固定宽度，方便按「动作数 × 宽度」算吸附距离）。 */
@Composable
private fun SwipeActionButton(action: SwipeAction, onDone: () -> Unit) {
    Box(
        modifier = Modifier
            .width(ACTION_WIDTH)
            .fillMaxHeight()
            .background(action.backgroundColor)
            .clickable {
                action.onClick()
                onDone()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = action.contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 单个动作按钮的宽度（dp）。 */
private val ACTION_WIDTH = 64.dp
