package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 分组标题 —— Phase 26 方案的分组语言。
 *
 * 现状的分组标题有两种画法：清单页的 `ChecklistSectionHeader`（14sp 品牌蓝 +
 * 分隔线，`ChecklistTab.kt:775`），其余页面多数直接不画标题。
 * 这套方案统一成「13.5sp 粗体 + 条数」，**不画分隔线** ——
 * 组与组之间靠留白分隔，而不是靠一条线。
 *
 * @param title 组名
 * @param count 条数（跟在组名后面，11.5sp）
 * @param action 右侧动作（如「优先级排序 ⌄」），右对齐
 */
@Composable
fun SectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.weight(1f))
            action()
        }
    }
}
