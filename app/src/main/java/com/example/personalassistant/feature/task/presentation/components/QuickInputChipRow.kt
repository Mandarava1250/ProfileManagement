package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.task.domain.model.ParsedQuickInput
import com.example.personalassistant.feature.task.domain.model.QuickInputToken
import com.example.personalassistant.feature.task.domain.model.QuickInputTokenKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 快速添加条上方的**解析结果 chip 行**。
 *
 * 用户在输入框里写「买牛奶 明天 #Work ~学习」，这一行就显示三枚 chip：
 * 日期（显示成具体日期）、标签、清单。
 *
 * **点 chip = 把这一项从输入文本里去掉** —— 靠 [QuickInputToken.range]
 * （这段在原始输入里的下标区间）定位，删完重新解析，对应的 chip 就没了。
 *
 * ## Phase 26 的两处改动
 *
 * 1. **chip 不再是底色胶囊**，改成「类型图标 + 文字」，跟清单项的 meta 区统一 ——
 *    之前一行挤着几个 `primaryContainer` 药丸，视觉噪音比信息量还大。
 * 2. **固定高度**：以前 `if (tokens.isEmpty()) return` 直接不画，
 *    输入框上方会凭空多出 / 少掉一整行，整个头部跟着跳。
 *    现在无论有没有 token，这一行都占同样的高度。
 */
@Composable
internal fun QuickInputChipRow(
    parsed: ParsedQuickInput,
    onRemove: (QuickInputToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = parsed.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg)
            // 固定高度 —— 有没有 token 都占这一条，头部不会跳。
            // 44dp 也是每枚 chip 的触达高度（chip 撑满整行高）。
            .height(44.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
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
 * 一枚 chip：类型图标 + 文案 + 「×」（点一下就删掉这一项）。
 *
 * 三种类型靠图标区分（DateRange / LocalOffer / List），不再靠底色。
 */
@Composable
private fun QuickInputChip(
    token: QuickInputToken,
    dueDate: Long?,
    onClick: () -> Unit,
) {
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

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            // 右侧留白把相邻两枚 chip 分开
            .padding(end = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Rounded.Close,
            // ⚠️ 硬编码文案：Phase 18.3 的约束是不动 res/strings.xml
            contentDescription = "移除这一项",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
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
