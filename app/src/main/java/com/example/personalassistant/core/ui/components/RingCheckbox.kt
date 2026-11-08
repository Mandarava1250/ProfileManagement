package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自绘圆环勾选框 —— Phase 26 方案的清单行语言。
 *
 * 不用 M3 的 `Checkbox`：它带一个 20dp 的方框填充 + 系统动画，
 * 而这套方案要的是「细线圆环 + 品牌蓝实心 + 白勾」，
 * 并且行与行之间靠留白分隔，勾选框不该再抢一块色块。
 *
 * ## 触达区怎么解决的
 *
 * Compose 没有负边距，所以这里：
 * - **布局槽**占 [size] + 8dp 间隙（视觉圆环 + 右侧留白）
 * - **触达区**是 44dp 的方块，用 `offset` 叠在槽位上（不占布局、不会把文字挤走）
 *
 * 44dp 触达区在竖直方向会超出槽位 12dp，但清单行本身有 60dp+ 高，
 * 上下都是留白，不会碰到相邻行的内容。
 *
 * @param checked 是否已勾选
 * @param onCheckedChange 勾选状态变化回调
 * @param size 圆环视觉直径，默认 20dp
 * @param touchTarget 触达区直径，默认 44dp（Material 最小值）
 * @param enabled 是否可交互
 */
@Composable
fun RingCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    touchTarget: Dp = 44.dp,
    enabled: Boolean = true,
) {
    val brand = MaterialTheme.colorScheme.primary
    val uncheckedRing = MaterialTheme.colorScheme.outline
    val disabledRing = MaterialTheme.colorScheme.outlineVariant

    // 视觉圆环居中于 44dp 触达区：44 的中心要落在视觉的中心上
    val offsetX = -(touchTarget - size) / 2

    Box(
        modifier = modifier.size(size + 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(touchTarget)
                .offset(x = offsetX)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val stroke = size.toPx() * 0.085f
                val radius = size.toPx() / 2f - stroke / 2f
                val center = Offset(size.toPx() / 2f, size.toPx() / 2f)

                if (checked) {
                    drawCircle(color = brand, radius = radius, center = center)
                    val w = size.toPx()
                    val tick = Path().apply {
                        moveTo(w * 0.28f, w * 0.52f)
                        lineTo(w * 0.44f, w * 0.68f)
                        lineTo(w * 0.74f, w * 0.34f)
                    }
                    drawPath(
                        path = tick,
                        color = Color.White,
                        style = Stroke(
                            width = stroke * 1.1f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                } else {
                    drawCircle(
                        color = if (enabled) uncheckedRing else disabledRing,
                        radius = radius,
                        center = center,
                        style = Stroke(width = stroke),
                    )
                }
            }
        }
    }
}
