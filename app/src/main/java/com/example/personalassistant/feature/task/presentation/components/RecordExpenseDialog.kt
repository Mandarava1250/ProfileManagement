package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import com.example.personalassistant.feature.task.domain.model.Task

/**
 * Phase 21.1：完成带金额的清单项后弹「记一笔？」
 *
 * 分类必须**用户选** —— Phase 21.3 的铁律：自动记账要给同账户的分类，
 * 分类缺失时让用户选，不静默失败。所以一个分类都没配时，确认按钮是禁用的，
 * 并提示先去记账页建个分类。
 */
@Composable
fun RecordExpenseDialog(
    task: Task,
    categories: List<Category>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedCategoryId by remember(task.id) { mutableStateOf<Long?>(null) }
    val selected = categories.firstOrNull { it.id == selectedCategoryId }
    val amount = task.amount ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一笔？") },
        text = {
            Column {
                Text(
                    text = "「${task.title}」花了 ${MoneyFormat.format(amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "要记进支出账吗？",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (categories.isEmpty()) {
                    Text(
                        text = "还没有支出分类 —— 先去记账页建一个，才能记账。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    Text(
                        text = "选个分类：",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = selectedCategoryId == category.id,
                                onClick = { selectedCategoryId = category.id },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onConfirm(it.id) } },
                enabled = selected != null,
            ) {
                Text("记一笔")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("以后再说")
            }
        },
    )
}
