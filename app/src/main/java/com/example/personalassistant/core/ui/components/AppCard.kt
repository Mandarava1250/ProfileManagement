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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.AppShadows
import com.example.personalassistant.core.ui.theme.AppSpacing

/**
 * 统一卡片骨架 —— Phase 26「秩序 / 留白」方案
 *
 * 统一了什么：
 * - 圆角：默认 20dp（[AppRadii.card]），本次改造的页面显式传 14dp（[AppRadii.card14]）
 * - 阴影：品牌色 tinted（[AppShadows.rest]），不是 Material 默认的纯黑
 * - 左侧 3dp 语义色条：可选，用来标出「这条属于收入 / 支出 / 当前时刻」
 *
 * ## Phase 26 的两处改动
 *
 * 1. **加了 [shape] 参数**：[AppRadii.card] 有 11 个既有调用点，直接把常量从 20 改成 14
 *    会把本次范围之外的页面一起带偏，所以圆角走参数，本次涉及的四模块显式传 14dp。
 * 2. **色条改成绝对定位**：原来色条是「内容整体右移 4dp + 覆盖条」，
 *    新方案要求色条压在卡片左缘、**不挤内容**（3dp 竖条正好落在内容内边距里）。
 *
 * 什么时候用：真正的卡片（预算卡、设置项卡）。
 * 列表行（`TransactionRow`、清单行、课表格子）不加卡片外壳 —— 那是另一种形态，别硬套。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.md),
    elevated: Boolean = true,
    shape: Shape = RoundedCornerShape(AppRadii.card),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val accentWidth = 3.dp

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
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        top = contentPadding.calculateTopPadding(),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    content = content,
                )
                if (accent != null) {
                    // 左侧 3dp 语义色条。
                    //
                    // 画在**卡片内容层**里（而不是卡片外面）：Material3 的 Surface 会用
                    // 自己的 shape 裁剪内容，色条自然跟着圆角走。
                    // 早先的写法是在 Card 外面手工重建左圆角，可 RoundedCornerShape
                    // 的圆角存的是 CornerSize（可能是百分比），拿不到绝对值 ——
                    // 那段重建代码在当前 Compose 版本下根本编译不过。
                    //
                    // matchParentSize 不参与尺寸计算，只跟随父级已定好的尺寸，
                    // 所以色条既不会把内容撑高，也不会挤走内容。
                    Box(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .width(accentWidth)
                                .fillMaxHeight()
                                .background(accent),
                        )
                    }
                }
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
