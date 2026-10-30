package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.AppShadows
import com.example.personalassistant.core.ui.theme.AppSpacing

/**
 * 统一卡片骨架 —— 樱花粉 2.0
 *
 * 统一了什么：
 * - 圆角：20dp（[AppRadii.card]），全 App 只有这一档
 * - 阴影：暖粉 tinted（[AppShadows.rest]），不是 Material 默认的纯黑
 * - 左侧 4dp 语义色条：可选，用来标出「这条属于收入 / 支出 / 优先级高」
 *
 * 什么时候用：真正的卡片（任务卡、预算卡、设置项卡）。
 * 列表行（[com.example.personalassistant.feature.finance.presentation.components.TransactionRow]）
 * 与课表格子（CourseCard）不加卡片外壳 —— 那是另一种形态，别硬套。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.md),
    elevated: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AppRadii.card)
    Card(
        modifier = modifier
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = 3.dp,
                        shape = shape,
                        ambientColor = AppShadows.rest,
                        spotColor = AppShadows.rest,
                    )
                } else {
                    Modifier
                }
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            if (accent != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = AppRadii.card, bottomStart = AppRadii.card))
                        .background(accent),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                content = content,
            )
        }
    }
}

/**
 * 元信息胶囊 —— 卡片里的次级信息（标签、截止时间、账户名）
 *
 * 统一的圆角 12dp + 紧凑内边距，全 App 一样。
 */
@Composable
fun AppMetaChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.chip))
            .background(containerColor)
            .padding(horizontal = AppSpacing.xs, vertical = 3.dp),
    )
}
