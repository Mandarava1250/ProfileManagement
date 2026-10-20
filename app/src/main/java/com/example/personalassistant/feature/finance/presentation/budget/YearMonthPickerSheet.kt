package com.example.personalassistant.feature.finance.presentation.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import java.time.YearMonth

/**
 * C 重构阶段 9：年份月份选择 Sheet
 *
 * - 列出"已持久化的预算月份"（VM 端 combine 出）+ 兜底：[当前月]、[上月]
 * - 点击某月 → [onPick]，由 UI 关 Sheet
 *
 * 真正的可选列表由 [availableMonths] 传入；本组件只负责渲染。
 */
@Composable
internal fun YearMonthPickerSheet(
    currentYearMonth: String,
    availableMonths: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 兜底：把当前月 / 当月 / 上月也补进去
    val now = remember { FinanceDateUtils.currentYearMonth() }
    val mergedMonths = remember(currentYearMonth, availableMonths) {
        val nowYm = YearMonth.parse(now)
        val fallback = listOf(currentYearMonth, nowYm.toString(), nowYm.minusMonths(1).toString())
        (availableMonths + fallback).distinct().sortedDescending()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "选择月份",
                style = MaterialTheme.typography.titleLarge,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(mergedMonths) { ym ->
                    val isCurrent = ym == currentYearMonth
                    TextButton(
                        onClick = { onPick(ym) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isCurrent) "$ym（当前）" else ym,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrent)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}