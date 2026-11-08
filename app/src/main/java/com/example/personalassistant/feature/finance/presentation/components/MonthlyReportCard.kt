package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as GraphicsColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.core.ui.theme.LocalAppSurfaces
import com.example.personalassistant.feature.finance.domain.model.MonthlyReport
import com.example.personalassistant.feature.finance.presentation.util.IconRegistry
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import com.example.personalassistant.core.ui.theme.onColorFor

/**
 * Phase 7b：月度报表卡片（主页中部）
 *
 * - 顶部三段：支出 / 收入 / 结余（横排）
 * - 中部：Top 5 支出分类（横条 + 百分比 + 分类名 + icon）
 * - 底部：本月按日支出趋势（简笔折线 Canvas，避免引入 MPAndroidChart，7c 再升级）
 *
 * 7b 范围克制：纯 Compose + Canvas，零依赖。
 *
 * @param categoryById 分类 id → Category（用于解析 icon + 颜色）
 * @param billingRangeText 账期区间副标题（如 "10-15 ~ 11-14"）；null 则不显示
 * @param onDayBucketClick 可选：点击折线上某一天 → 跳明细（7b 暂不接，留接口）
 */
@Composable
fun MonthlyReportCard(
    report: MonthlyReport?,
    categoryById: Map<Long, Category>,
    modifier: Modifier = Modifier,
    billingRangeText: String? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        // Phase 26：16dp → 14dp（本次改造统一的卡片圆角），底色改暖白。
        shape = RoundedCornerShape(AppRadii.card14),
        colors = CardDefaults.cardColors(containerColor = LocalAppSurfaces.current.paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 标题
            Text(
                text = "本月报表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // 账期区间副标题：截止日 ≠ 1 号时，"本月"不是自然月，必须说清楚是哪几天
            if (billingRangeText != null) {
                Text(
                    text = billingRangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (report == null) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                return@Column
            }

            // Phase 9b D2：预算执行条（报表顶部，百分比 + 进度 + 点击展开明细）。
            // report.budget 为 null（用户未设预算）时整块省略 —— 不显示空壳。
            BudgetProgressBar(
                progress = BudgetProgress.fromOrNull(
                    budgetAmount = report.budget?.totalAmount,
                    spentAmount = report.totalExpense,
                ),
            )

            // 顶部三段：支 / 收 / 结余
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryItem(
                    label = "支出",
                    amount = report.totalExpense,
                    // Phase 26：改固定语义色。「支出红」不该跟着用户主题色变 ——
                    // 用户把主题换成绿色时，花出去的钱不能变成绿的。
                    color = LocalAppColors.current.expenseInk,
                )
                SummaryItem(
                    label = "收入",
                    amount = report.totalIncome,
                    color = LocalAppColors.current.incomeInk,
                )
                SummaryItem(
                    label = "结余",
                    amount = report.balance,
                    color = if (report.balance >= 0) LocalAppColors.current.incomeInk
                    else LocalAppColors.current.expenseInk,
                )
            }

            // Top 5 支出分类
            if (report.expenseByCategory.isNotEmpty()) {
                Text(
                    text = "Top 支出分类",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                val max = report.expenseByCategory.first().total.coerceAtLeast(0.001)
                report.expenseByCategory.take(5).forEach { sum ->
                    val cat = categoryById[sum.categoryId]
                    CategoryExpenseBar(
                        categoryName = cat?.name ?: "未分类",
                        icon = cat?.let { IconRegistry.resolve(it.iconName) }
                            ?: IconRegistry.DEFAULT_ICON,
                        color = cat?.let { GraphicsColor(it.color) }
                            ?: LocalAppColors.current.unknownCategory,
                        amount = sum.total,
                        ratio = (sum.total / max).coerceIn(0.0, 1.0).toFloat(),
                    )
                }
            }

            // 月趋势（简笔折线）
            if (report.monthExpenseTrend.size >= 2) {
                Text(
                    text = "近 30 天支出趋势",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                ExpenseTrendCanvas(
                    trend = report.monthExpenseTrend.map { it.total },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, amount: Double, color: GraphicsColor) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            text = MoneyFormat.format(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun CategoryExpenseBar(
    categoryName: String,
    icon: ImageVector,
    color: GraphicsColor,
    amount: Double,
    ratio: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = CircleShape,
            color = color,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onColorFor(color),
                modifier = Modifier.padding(4.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = MoneyFormat.format(amount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 简笔进度条：背景灰条 + 前景实色
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(2.dp),
                ) {}
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .height(4.dp),
                    color = color,
                    shape = RoundedCornerShape(2.dp),
                ) {}
            }
        }
    }
}

/** 简笔折线 + 圆点（Canvas，避免引入图表库） */
@Composable
private fun ExpenseTrendCanvas(
    trend: List<Double>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Canvas(modifier = modifier) {
        if (trend.size < 2) return@Canvas
        val max = trend.max().coerceAtLeast(0.001)
        val min = 0.0
        val w = size.width
        val h = size.height
        val step = w / (trend.size - 1).coerceAtLeast(1)

        // 1. 横向参考线（中位线 50%）
        val midY = h * (1 - (max - min) / 2 / max).toFloat()
        drawLine(
            color = guideColor,
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )

        // 2. 折线
        val path = Path()
        trend.forEachIndexed { idx, value ->
            val x = (idx * step).toFloat()
            val y = (h * (1 - (value - min) / (max - min).coerceAtLeast(0.001))).toFloat()
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx()),
        )

        // 3. 圆点
        trend.forEachIndexed { idx, value ->
            val x = (idx * step).toFloat()
            val y = (h * (1 - (value - min) / (max - min).coerceAtLeast(0.001))).toFloat()
            drawCircle(
                color = pointColor,
                radius = 3.dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}
