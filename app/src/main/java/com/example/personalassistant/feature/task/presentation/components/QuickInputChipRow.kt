package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.task.domain.model.ParsedQuickInput
import com.example.personalassistant.feature.task.domain.model.QuickInputToken
import com.example.personalassistant.feature.task.domain.model.QuickInputTokenKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 18.3：快速添加条上方的**解析结果 chip 行**。
 *
 * 用户在输入框里写「买牛奶 明天 #Work ~学习」，这一行就显示三枚 chip：
 * 日期（显示成具体日期）、标签、清单。
 *
 * **点 chip = 把这一项从输入文本里去掉** —— 靠 [QuickInputToken.range]
 * （这段在原始输入里的下标区间）定位，删完重新解析，对应的 chip 就没了。
 *
 * @param parsed 当前输入的解析结果
 * @param onRemove 点 chip（回调带上被点的那个 token）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickInputChipRow(
    parsed: ParsedQuickInput,
    onRemove: (QuickInputToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = parsed.tokens
    if (tokens.isEmpty()) return

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
    ) {
        tokens.forEach { token ->
            QuickInputChip(
                token = token,
                dueDate = parsed.dueDate,
                onClick = { onRemove(token) },
            )
        }
    }
}

/**
 * 一枚 chip：左边的类型图标 + 文案 + 右边的「×」（点一下就删掉这一项）。
 *
 * 三种类型用不同的容器色区分（日期 / 标签 / 清单）。
 */
@Composable
private fun QuickInputChip(
    token: QuickInputToken,
    dueDate: Long?,
    onClick: () -> Unit,
) {
    // Phase 26：三种 token 曾各用一种容器色（primary / tertiary / secondary），
    // 快速添加行里几个 chip 挤在一起就成了一块块杂色。
    // 现在统一成一套 —— 类型靠图标（DateRange / LocalOffer / List）区分，不靠颜色。
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val icon = when (token.kind) {
        QuickInputTokenKind.DATE -> Icons.Rounded.DateRange
        QuickInputTokenKind.TAG -> Icons.Rounded.LocalOffer
        QuickInputTokenKind.LIST -> Icons.Rounded.List
    }
    // 日期 chip 显示**具体日期**（「明天」→ 11/4），不是那个词本身
    val label = if (token.kind == QuickInputTokenKind.DATE && dueDate != null) {
        concreteDateLabel(dueDate)
    } else {
        token.label
    }

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Rounded.Close,
                // ⚠️ 硬编码文案：Phase 18.3 的约束是不动 res/strings.xml
                contentDescription = "移除这一项",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 具体日期（同年 `M/d`，跨年 `Y/M/d`）。
 *
 * chip 上要回答「解析成了哪天」，而不是重复用户写的那个词。
 */
private fun concreteDateLabel(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return if (date.year == today.year) {
        "${date.monthValue}/${date.dayOfMonth}"
    } else {
        "${date.year}/${date.monthValue}/${date.dayOfMonth}"
    }
}
