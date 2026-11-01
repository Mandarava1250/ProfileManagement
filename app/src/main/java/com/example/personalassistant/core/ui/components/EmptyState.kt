package com.example.personalassistant.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppSpacing

/**
 * 全 App 统一的空态 —— Phase 14 视觉收口
 *
 * 之前每个页面的空态各写各的：有的只有一行字，有的塞一大段说明，
 * 有的带插画有的不带，风格完全对不上。这里收敛成一套：
 *
 * **插画 → 标题 → 说明 → 主行动按钮**
 *
 * @param illustration 插画资源（`res/drawable` 里的 `ic_empty_*`）
 * @param title 一句话概括现状
 * @param description 告诉他下一步能做什么
 * @param action 主行动按钮，没有就传 null
 */
@Composable
fun EmptyState(
    @DrawableRes illustration: Int,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(illustration),
            contentDescription = null,
            modifier = Modifier.size(112.dp),
        )
        Spacer(Modifier.height(AppSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(AppSpacing.md))
            action()
        }
    }
}
