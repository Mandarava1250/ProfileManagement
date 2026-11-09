package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppSurfaces
import com.example.personalassistant.core.utils.DateUtils

/**
 * 日程流 —— 日历主视图（Phase 26）。
 *
 * 一行 = 一件事：44dp 时刻列 + 7dp 圆点 + 标题 + 次要信息。
 *
 * ## 为什么要有这个视图
 *
 * 月 / 周 / 日三种网格都在回答「什么时候有空」，不回答「那天具体要做什么」。
 * 用户打开日历九成是为了确认「接下来几点要做什么」，日程流直接给这条竖线。
 *
 * 三种网格没有消失 —— 它们降级成下面的标签页，看分布时随时切过去。
 */
@Composable
fun AgendaStream(
    days: List<AgendaDay>,
    modifier: Modifier = Modifier,
    onItemClick: (AgendaItem) -> Unit = {},
    lazy: Boolean = true,
) {
    val surfaces = LocalAppSurfaces.current
    if (lazy) {
        LazyColumn(modifier = modifier.fillMaxWidth()) {
            days.forEach { day ->
                item(key = "day-${day.date}") { AgendaDayTitle(day) }
                items(day.items, key = { it.key }) { item ->
                    AgendaRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        dividerColor = surfaces.divider,
                    )
                }
            }
        }
    } else {
        // 非惰性版：给「外层已经有一个滚动条」的场合用。
        // LazyColumn 不能嵌在 verticalScroll 里（无限高度约束会崩），
        // 所以这里用普通 Column 全量渲染 —— 一周的数据量完全扛得住。
        Column(modifier = modifier.fillMaxWidth()) {
            days.forEach { day ->
                AgendaDayTitle(day)
                day.items.forEach { item ->
                    AgendaRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        dividerColor = surfaces.divider,
                    )
                }
            }
        }
    }
}

/** 一天的小标题（日期） */
@Composable
private fun AgendaDayTitle(day: AgendaDay) {
    Text(
        text = DateUtils.formatChineseDate(day.date),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(
            horizontal = AppSpacing.md,
            vertical = AppSpacing.xs,
        ),
    )
}

/** 一行事项 */
@Composable
private fun AgendaRow(
    item: AgendaItem,
    onClick: () -> Unit,
    dividerColor: Color,
) {
    // 任务没有自己的颜色 —— 用品牌蓝，跟日程 / 课程的分类色区分开
    val dotColor = when (item.kind) {
        AgendaKind.EVENT, AgendaKind.COURSE -> Color(item.color)
        AgendaKind.TASK -> MaterialTheme.colorScheme.primary
    }
    val titleColor = if (item.isCompleted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        // 44dp 时刻列 —— 全天事项写「全天」
        Text(
            text = item.timeText ?: "全天",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle != null) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
    // 行间 1px 分隔
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(dividerColor),
    )
}
