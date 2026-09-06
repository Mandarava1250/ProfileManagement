package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import com.example.personalassistant.feature.finance.presentation.util.IconRegistry
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * Phase 7a：交易行（一行展示一条流水）
 *
 * - 左侧：分类彩色圆形 + icon
 * - 中间：分类名 + 备注（或"无备注"）
 * - 右侧：金额（支出负红色 / 收入正绿色） + 时间
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    category: Category?,
    modifier: Modifier = Modifier,
) {
    val categoryName = category?.name ?: "未知分类"
    val categoryColor = category?.color?.let { Color(it) } ?: Color(0xFF9E9E9E)
    val iconName = category?.iconName ?: "Category"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 分类彩色圆形
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = categoryColor,
        ) {
            Icon(
                imageVector = IconRegistry.resolve(iconName),
                contentDescription = categoryName,
                tint = Color.White,
                modifier = Modifier.padding(8.dp),
            )
        }

        // 中间：分类名 + 备注
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = transaction.note?.takeIf { it.isNotBlank() } ?: "无备注",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        // 右侧：金额 + 时间
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val signed = when (transaction.type) {
                TransactionType.EXPENSE -> -transaction.amount
                TransactionType.INCOME -> transaction.amount
            }
            val color = when (transaction.type) {
                TransactionType.EXPENSE -> Color(0xFFD32F2F)
                TransactionType.INCOME -> Color(0xFF388E3C)
            }
            Text(
                text = MoneyFormat.format(signed),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Text(
                text = FinanceDateUtils.formatTimeOfDay(transaction.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
