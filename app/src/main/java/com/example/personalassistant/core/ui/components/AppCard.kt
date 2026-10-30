package com.example.personalassistant.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalLayoutDirection
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
    val layoutDirection = LocalLayoutDirection.current
    val accentWidth = 4.dp

    // Phase 14 修崩溃：这里原来用的是 Row(Modifier.height(IntrinsicSize.Min))。
    //
    // IntrinsicSize 会让父容器向下问子树的「内在高度」，
    // 而 LazyList / BoxWithConstraints / TabRow 这类 SubcomposeLayout 明确不支持 intrinsic 测量 ——
    // 一旦卡片里放进懒加载列表（标签行、月历网格…），整页直接崩。
    // 现在卡片自己决定尺寸，色条改用 matchParentSize 覆盖层画，不再需要 intrinsic。
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
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
            Column(
                modifier = Modifier.padding(
                    // 有色条时给内容让出 4dp，色条不会压在文字底下
                    start = contentPadding.calculateStartPadding(layoutDirection) +
                        if (accent != null) accentWidth else 0.dp,
                    top = contentPadding.calculateTopPadding(),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                content = content,
            )
        }
        if (accent != null) {
            // 左侧 4dp 语义色条：matchParentSize 只跟随卡片尺寸，不参与尺寸计算
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .width(accentWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = AppRadii.card, bottomStart = AppRadii.card))
                        .background(accent),
                )
            }
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
