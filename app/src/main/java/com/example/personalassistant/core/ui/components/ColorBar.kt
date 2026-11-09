package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * 3dp 分类色竖条 —— Phase 26 方案的「色只做标识」语言。
 *
 * 分类色 / 课程色 / 习惯色都是**用户数据**，浅色系很常见（内置「学习 #FFB74D」
 * 「交通 #64B5F6」都是 Material 300 系）。画成 3dp 竖条后面积更小、更容易糊掉，
 * 所以竖条统一走 [ensureBarContrast] 压暗到白底 ≥ 3:1 再画。
 *
 * 高度由调用方给（通常配 `fillMaxHeight()`），这里只管宽度与圆角。
 */
@Composable
fun ColorBar(
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 3.dp,
    corner: Dp = 1.5.dp,
) {
    Box(
        modifier
            .width(width)
            .clip(RoundedCornerShape(corner))
            .background(color)
    )
}

/**
 * 保证竖条色在浅底上看得见：对比度不够就逐档压暗。
 *
 * 竖条是「非文字的 UI 元素」，按 WCAG 1.4.11 需要 ≥ 3:1。
 * 浅色分类（#FFE0B2 只有 1.4:1）不处理的话，3dp 竖条在白底上基本等于不存在。
 *
 * @param color 原始色（通常是分类色 / 课程色）
 * @param minContrast 目标对比度（相对白底），默认 3:1
 */
fun ensureBarContrast(color: Color, minContrast: Float = 3f): Color {
    if (contrastRatioWithWhite(color) >= minContrast) return color
    var result = color
    // 每次压暗 10%，最多 24 步 —— 24 步是 0.9^24 ≈ 0.08，足够把任何浅色压到目标以下
    repeat(24) {
        if (contrastRatioWithWhite(result) >= minContrast) return result
        result = Color(
            red = result.red * 0.9f,
            green = result.green * 0.9f,
            blue = result.blue * 0.9f,
            alpha = result.alpha,
        )
    }
    return result
}

/** 相对白底的对比度（WCAG 定义）。 */
private fun contrastRatioWithWhite(color: Color): Float {
    val l = color.luminance()
    return (max(l, 1f) + 0.05f) / (min(l, 1f) + 0.05f)
}
