package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * Phase 9b D2：月度报表顶部的**预算执行条**。
 *
 * ## 为什么单独一个类型
 *
 * 报表卡（[MonthlyReportCard]）原本只展示「支出 / 收入 / 结余 + Top 分类 + 趋势」，
 * **没有预算维度** —— 也就是说用户能看到「花了多少」，看不到「预算还剩多少」。
 * `MonthlyReport.usedRatio` 字段其实早就算好了，只是没有任何 UI 用它。
 *
 * 这个类型把「预算执行」从报表里独立出来：
 * - 百分比 / 进度 / 剩余 / 超支 的推导是纯计算（[BudgetProgress]），JVM 毫秒级验证
 * - 渲染是无状态的（[BudgetProgressBar]），喂数据即可测
 *
 * ## 与 DailyPoolCard 的分工
 *
 * [DailyPoolCard] 管的是「今天还能花多少」（零钱池，按日粒度）；
 * 本组件管的是「这个月预算用了多少」（按整月粒度）。两者数据源不同、
 * 刷新节奏不同，强行合并会让池子的日切逻辑污染月度预算展示。
 */
data class BudgetProgress(
    /** 本月预算总额 */
    val budgetAmount: Double,
    /** 本月已支出 */
    val spentAmount: Double,
) {
    /**
     * 已用比例（可 > 1，表示超支）。
     *
     * 预算为 0 或负数时返回 0 —— 与 `MonthlyReport.usedRatio` 同一套保护，
     * 避免除零后显示 NaN%。
     */
    val ratio: Double
        get() {
            if (budgetAmount <= 0.0) return 0.0
            return spentAmount / budgetAmount
        }

    /** 进度条填充比例（钳制到 0..1，条形本身不会溢出） */
    val clampedRatio: Float get() = ratio.coerceIn(0.0, 1.0).toFloat()

    /** 百分比文案；超支时可能 > 100% */
    val percentText: String get() = "${(ratio * 100).toInt()}%"

    /** 剩余预算（超支为负） */
    val remaining: Double get() = budgetAmount - spentAmount

    /** 是否超支 */
    val isOverBudget: Boolean get() = spentAmount > budgetAmount

    companion object {
        /** 由报表字段构造；未设预算（budget 为 null）时返回 null，UI 整块省略 */
        fun fromOrNull(budgetAmount: Double?, spentAmount: Double): BudgetProgress? {
            val amount = budgetAmount ?: return null
            return BudgetProgress(budgetAmount = amount, spentAmount = spentAmount)
        }
    }
}

/**
 * 预算执行条（无状态）：进度条 + 百分比 + 已用/总额；点击展开明细。
 *
 * @param progress null 表示未设预算 —— 整个组件不渲染（不显示空壳）
 */
@Composable
fun BudgetProgressBar(
    progress: BudgetProgress?,
    modifier: Modifier = Modifier,
) {
    if (progress == null) return

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 第一行：标题 + 百分比
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "本月预算",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Text(
                text = progress.percentText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (progress.isOverBudget) LocalAppColors.current.expenseInk
                else MaterialTheme.colorScheme.primary,
            )
        }

        // 第二行：进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.clampedRatio)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        if (progress.isOverBudget) LocalAppColors.current.expenseInk
                        else MaterialTheme.colorScheme.primary,
                    ),
            )
        }

        // 第三行：摘要（已用 / 总额）
        Text(
            text = "已用 ${MoneyFormat.formatPlain(progress.spentAmount)}" +
                " / ${MoneyFormat.formatPlain(progress.budgetAmount)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 展开后的明细
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "剩余 ${MoneyFormat.formatPlain(progress.remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (progress.isOverBudget) {
                    Text(
                        text = "已超出预算 ${MoneyFormat.formatPlain(-progress.remaining)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.expenseInk,
                    )
                }
            }
        }
    }
}