package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * Phase 7a：当日零钱池卡片（顶部常驻）
 *
 * - 大字号显示当日剩余
 * - 副标题显示动态提示（本月已用 / 总额）
 * - 当 remainingAmount < 0 时显示红色高亮
 * - 当 dailyBudget == 0 且 remainingAmount == 0 时显示"未设置预算"占位
 */
@Composable
fun DailyPoolCard(
    pool: DailyPool?,
    monthBudget: Double?,
    monthExpense: Double?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "今日剩余",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )

            val remaining = pool?.remainingAmount ?: 0.0
            val isOverBudget = remaining < 0
            val amountColor = when {
                pool == null -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                isOverBudget -> Color(0xFFD32F2F) // 红
                else -> MaterialTheme.colorScheme.onPrimaryContainer
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
                        color = if (ratio > 1.0) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                    )
                }
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
