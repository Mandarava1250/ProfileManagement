package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 月份选择器 —— Phase 15 P1-2.3
 *
 * ## 为什么要有
 *
 * 月视图原本只能靠顶栏的「上一个 / 下一个」一格格挪，
 * 从 11 月回到 3 月要按 8 次。用户反馈里明确提到想要「点右上角日历图标直接选月份」。
 *
 * ## 形态
 *
 * 年份左右切换 + 12 个月的网格。当前月份高亮（选中态）。
 * 选完即跳，面板自己关掉。
 *
 * @param currentYearMonth 当前年月，形如 `2026-11`（与 [com.example.personalassistant.core.utils.DateUtils.currentYearMonth] 同格式）
 * @param onPick 选中某个月份（1..12）
 * @param onDismiss 关闭
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MonthPickerSheet(
    currentYearMonth: String,
    onPick: (year: Int, month: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember(currentYearMonth) {
        mutableStateOf(currentYearMonth.substringBefore('-').toIntOrNull() ?: 2026)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ===== 年份切换 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { year-- }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一年")
                }
                Text(
                    text = "$year 年",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { year++ }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "下一年")
                }
            }

            // ===== 12 个月 =====
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..12).forEach { month ->
                    val picked = currentYearMonth == "%04d-%02d".format(year, month)
                    FilterChip(
                        selected = picked,
                        onClick = { onPick(year, month) },
                        label = { Text("$month 月") },
                    )
                }
            }
        }
    }
}
