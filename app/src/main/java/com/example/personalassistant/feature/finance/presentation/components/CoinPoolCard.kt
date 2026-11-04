package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.personalassistant.core.ui.components.AppCard
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.finance.domain.model.CoinPool
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 批次 D：零钱池卡片（只读）
 *
 * ## 语义
 *
 * 用户新要求：「零钱池 = 今日剩余在今日结束后自动存入，用户无法自行更改余额」。
 * 所以这张卡**没有任何写入口** —— 没有「存入 / 取出」按钮，没有编辑弹窗，
 * 用户能做的只有看。
 *
 * ## 与「今日剩余」卡的关系
 *
 * 两张卡都是「钱」，但语义不同：
 * - 「今日剩余」= 今天还能花多少（当天有效，整天在变）
 * - 「零钱池」= 历次日终结转累加下来的钱（每天只在日终变一次）
 *
 * 底部那行「日终自动存入零钱池」就是在「今日剩余」卡上点明这层关系。
 *
 * ## 负余额
 *
 * 余额可以为负（累计超支），显示为红色（[LocalAppColors] 的 expenseInk）。
 */
@Composable
fun CoinPoolCard(
    pool: CoinPool?,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current

    AppCard(
        modifier = modifier.fillMaxWidth(),
        accent = appColors.budgetInk,
        contentPadding = PaddingValues(AppSpacing.lg),
    ) {
        // ===== 标题行：图标 + 「零钱池」=====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "零钱池",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ===== 余额 =====
        // pool 为 null 表示「还没建零钱池」，与「余额是 0」是两回事，
        // 所以这里分开处理：null 走占位文案，非 null 才谈正负。
        val balance = pool?.balance ?: 0.0
        Text(
            text = if (pool == null) "还没建零钱池" else MoneyFormat.format(balance),
            style = MaterialTheme.typography.headlineMedium,
            color = when {
                pool == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                balance < 0 -> appColors.expenseInk          // 红：欠账
                else -> MaterialTheme.colorScheme.onSurface
            },
        )

        // ===== 副标题：说明这个数字是怎么来的 =====
        Text(
            text = "每日自动结转：把当天没花完的额度存进来",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ===== 底部一行：最近一次结算 =====
        // 余额是「历次结算累加」，所以必须让用户能看到「累到哪天为止」，
        // 否则一个只涨不动的数字看起来就像坏了。
        Text(
            text = lastSettledLabel(pool),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 「最近结算：X 月 X 日」/「尚未结算」
 *
 * 从 `lastSettledDate`（已结算到的最后一天，当天 00:00 时间戳）还原成本地日期。
 */
private fun lastSettledLabel(pool: CoinPool?): String {
    if (pool == null || !pool.hasSettled) return "尚未结算"
    val date = Instant.ofEpochMilli(pool.lastSettledDate)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return "最近结算：${formatSettledDate(date)}"
}

/** 日期格式：10 月 5 日 */
private fun formatSettledDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M 月 d 日"))
