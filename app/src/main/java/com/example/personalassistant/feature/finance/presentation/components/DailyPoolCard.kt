package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.components.AppCard
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * Phase 7b：当日零钱池卡片（顶部常驻）
 *
 * - 大字号显示当日剩余
 * - 副标题显示动态提示（本月已用 / 总额）
 * - 当 remainingAmount < 0 时显示红色高亮
 * - 当 remainingAmount < warningThreshold 时显示橙色（7b 增量）
 * - 当 dailyBudget == 0 且 remainingAmount == 0 时显示"未设置预算"占位
 *
 * Phase 14：外壳换成 [AppCard]（统一圆角 + 暖粉阴影），
 * 超支红 / 警告橙改走语义色 token，不再写死色值。
 *
 * @param warningThreshold 警告阈值（剩余金额低于此值时显示橙色）。0 表示禁用。
 */
@Composable
fun DailyPoolCard(
    pool: DailyPool?,
    monthBudget: Double?,
    monthExpense: Double?,
    warningThreshold: Double = 0.0,
    modifier: Modifier = Modifier,
) {
    // Phase 14：语义色统一从 LocalAppColors 取
    val appColors = LocalAppColors.current
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(AppSpacing.lg),
    ) {
        Text(
            text = "今日剩余",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )

        val remaining = pool?.remainingAmount ?: 0.0
        val amountColor = when {
            pool == null -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            remaining < 0 -> appColors.expenseInk          // 红：超支
            warningThreshold > 0.0 && remaining < warningThreshold -> appColors.budgetInk // 橙：警告
            else -> MaterialTheme.colorScheme.onPrimaryContainer // 默认
        }
        Text(
            text = if (pool == null) "未设置预算" else MoneyFormat.format(remaining),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = amountColor,
        )

        if (pool != null) {
            // 副标题：日预算 + 本月已用比例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "日预算 ${MoneyFormat.format(pool.dailyBudget)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = monthlyUsageText(monthBudget, monthExpense),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }

            // 进度条：本月已用 / 月预算
            val ratio = monthlyUsageRatio(monthBudget, monthExpense)
            if (ratio != null) {
                LinearProgressIndicator(
                    progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (ratio > 1.0) appColors.expenseInk else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                )
            }
        }
    }
}

private fun monthlyUsageText(monthBudget: Double?, monthExpense: Double?): String {
    if (monthBudget == null || monthBudget <= 0.0) return "本月 —"
    val expense = monthExpense ?: 0.0
    val percent = MoneyFormat.formatPercent(expense / monthBudget)
    return "本月已用 $percent"
}

private fun monthlyUsageRatio(monthBudget: Double?, monthExpense: Double?): Double? {
    if (monthBudget == null || monthBudget <= 0.0) return null
    val expense = monthExpense ?: 0.0
    return expense / monthBudget
}
