package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Phase 8 Sub-step 9 Step 4.2：冲突角标（DayDetailSheet 行末用）
 *
 * 涟宝解读：
 * - [hasConflict]=false → 不渲染（占位 0 像素）
 * - [hasConflict]=true && [count]≤1 → "⚠ 冲突"
 * - [hasConflict]=true && [count]≥2 → "⚠ N 门冲突"
 *
 * 颜色：errorContainer 背景 + error 边框/图标 + onErrorContainer 文字
 * 来自 MaterialTheme，自动跟主题色（含深色模式）
 */
@Composable
fun ConflictBadge(
    hasConflict: Boolean,
    count: Int = 0,
    modifier: Modifier = Modifier,
) {
    if (!hasConflict) return
    val text = if (count >= 2) "⚠ $count 门冲突" else "⚠ 冲突"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "冲突",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * 仅供 Preview / 内部使用的占位，使 lint 不警告 fillMaxWidth 未使用
 */
@Suppress("unused")
@Composable
internal fun ConflictBadgeRowPreview() {
    Row(modifier = Modifier.fillMaxWidth()) {
        ConflictBadge(hasConflict = true, count = 1)
    }
}