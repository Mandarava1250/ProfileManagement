package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.personalassistant.core.ui.components.AppCard
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingResult
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * Phase 7b：当日零钱池卡片（顶部常驻）
 *
 * ## 批次 C：大字号改成「纯计算」，按口径二选一
 *
 * 原来大字号直接显示 `pool.remainingAmount`（读 `daily_pools` 表），
 * 改预算后那个值纹丝不动（`refreshDailyPool` 的幂等守卫把它跳过了）。
 * 现在大字号来自 [TodayRemainingResult.amount] —— 由「本月可用余额 − 本月已支出」
 * 现算，改预算立刻生效。
 *
 * ## 两种口径下这张卡分别显示什么
 *
 * | | FIXED（固定） | AUTO（自动） |
 * |---|---|---|
 * | 大字号 | 本月剩余额度 | 今日可用 = 本月剩余 ÷ 剩余天数 |
 * | 副标题左 | 本月预算 ¥3,000.00 | 本月剩余 ¥2,000.00 · 按剩余 10 天分摊 |
 * | 副标题右 | 本月已用 33% | 本月已用 33% |
 *
 * 副标题左之所以按模式换：FIXED 下大数字已经是「本月剩余」，
 * 再写一遍是重复；写「本月预算」（总额）才有信息量。
 *
 * Phase 14：外壳换成 [AppCard]（统一圆角 + 暖粉阴影），
 * 超支红 / 警告橙改走语义色 token，不再写死色值。
 *
 * @param result 今日剩余的派生结果（金额 / 本月剩余 / 剩余天数 / 口径）
 * @param warningThreshold 警告阈值（剩余金额低于此值时显示橙色）。0 表示禁用。
 * @param onToggleMode 点击右上角口径标签时的回调；null = 不可切换（只读展示）
 */
@Composable
fun DailyPoolCard(
    result: TodayRemainingResult,
    monthBudget: Double?,
    monthExpense: Double?,
    warningThreshold: Double = 0.0,
    onToggleMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Phase 14：语义色统一从 LocalAppColors 取
    val appColors = LocalAppColors.current
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(AppSpacing.lg),
    ) {
        // ===== 标题行：左「今日剩余」+ 右「口径标签」（可点切换）=====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "今日剩余",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Text(
                text = result.mode.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = if (onToggleMode != null) {
                    Modifier.clickable { onToggleMode() }
                } else {
                    Modifier
                },
            )
        }

        // ===== 大字号：按口径二选一（结果为 null = 未设预算）=====
        val remaining = result.amount
        val amountColor = when {
            remaining == null -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            remaining < 0 -> appColors.expenseInk          // 红：超支
            warningThreshold > 0.0 && remaining < warningThreshold -> appColors.budgetInk // 橙：警告
            else -> MaterialTheme.colorScheme.onPrimaryContainer // 默认
        }
        Text(
            text = if (remaining == null) "未设置预算" else MoneyFormat.format(remaining),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = amountColor,
        )

        // ===== 副标题：模式相关的补充说明 + 本月已用 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = subtitleLeftText(result),
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

/**
 * 副标题左边的文案（按口径）
 *
 * - FIXED：大数字已经是「本月剩余」→ 这里给「本月预算」（总额），不重复
 * - AUTO ：大数字是「今日可用」→ 这里给「本月剩余」+ 分摊天数
 */
private fun subtitleLeftText(result: TodayRemainingResult): String = when (result.mode) {
    TodayRemainingMode.FIXED -> {
        val total = result.monthRemaining
        if (total == null) "本月 —" else "本月预算 ${MoneyFormat.format(total)}"
    }
    TodayRemainingMode.AUTO -> {
        val remaining = result.monthRemaining
        if (remaining == null) {
            "本月 —"
        } else {
            "本月剩余 ${MoneyFormat.format(remaining)} · 按剩余 ${result.remainingDays} 天分摊"
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
