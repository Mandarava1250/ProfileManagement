package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.LocalAppSurfaces

/**
 * 细进度条 —— Phase 26 方案里的进度条统一形态。
 *
 * 现状的进度条有 4dp（M3 `LinearProgressIndicator`）、6dp（`CompletionStatsCard`）、
 * 8dp（`BudgetProgressBar`）三种高度。这套方案统一成 2dp / 3dp 两档：
 * 进度是辅助信息，不该跟主数字抢视觉重量。
 *
 * @param progress 进度 0f..1f，超出范围会被钳制
 * @param height 条高，默认 3dp
 * @param trackColor 轨道色，默认暖白
 * @param progressColor 进度色，默认品牌色
 */
@Composable
fun ThinProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    trackColor: Color = LocalAppSurfaces.current.barTrack,
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .clip(RoundedCornerShape(height / 2))
                .background(progressColor)
        )
    }
}
