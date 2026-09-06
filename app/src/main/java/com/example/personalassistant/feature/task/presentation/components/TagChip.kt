package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.Tag

/**
 * Phase 6c：通用标签 chip。
 *
 * - [tag]：要展示的标签（必须含 displayName + colorHex）
 * - [selected]：true = 已选中态（用于 TaskDetailSection 已选 chip，点击 = 卸下）
 *   false = 未选中态（用于 TagFilterBar，点击 = 应用筛选）
 * - [onClick]：点击回调（VM 端处理 add / remove / filter）
 *
 * 颜色规则：
 * - 已选中态：背景 = tag.colorHex（带 alpha 让文字可读）
 * - 未选中态：边框 = tag.colorHex，背景透明
 *
 * 不依赖 MaterialTheme.colorScheme，仅用 tag.colorHex 即可保持品牌一致性。
 */
@Composable
fun TagChip(
    tag: Tag,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tagColor = Color(tag.colorHex)
    val shape = RoundedCornerShape(50) // pill

    val containerColor = if (selected) tagColor.copy(alpha = 0.85f) else Color.Transparent
    val contentColor = if (selected) onContainerColorFor(tagColor) else tagColor
    val borderStroke = if (selected) null else BorderStroke(1.5.dp, tagColor)

    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = borderStroke,
        tonalElevation = if (selected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(PaddingValues(horizontal = 12.dp, vertical = 6.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧小色点（视觉锚点，仅在未选中态显示）
            if (!selected) {
                Spacer(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tagColor)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = tag.displayName,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * 简单决定文字色：色板是深色 → 白色文字；浅色 → 黑文字。
 *
 * 用 W3C 推荐的相对亮度近似（用 Color.red*0.299 + green*0.587 + blue*0.114）。
 * 我们的 6 色都是浅亮色，所以走黑色字是 OK 的；这里留个保险逻辑未来适配。
 */
private fun onContainerColorFor(background: Color): Color {
    val brightness = (background.red * 0.299f +
        background.green * 0.587f +
        background.blue * 0.114f)
    return if (brightness > 0.6f) Color.Black else Color.White
}