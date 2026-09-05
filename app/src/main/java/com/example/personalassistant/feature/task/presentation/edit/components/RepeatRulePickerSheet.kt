package com.example.personalassistant.feature.task.presentation.edit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.RepeatRule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * 重复规则选择 BottomSheet
 *
 * - 顶部：6 个基础选项（不重复 / 每天 / 每周 / 每月 / 每年 / 自定义）
 * - 选中「自定义」：展开 WeekdayChips 让用户多选
 * - onConfirm：只在选了非 None 时才回调；选 None 时只关闭 sheet（不提交也可）
 * - onDismiss：关闭 sheet 不确认
 *
 * 设计说明：RepeatRule 内部用 sealed class，UI 只需把 RepeatRule 整体作为状态，
 * 切换基础选项会构造新对象，customDays 通过本地状态累积。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RepeatRulePickerSheet(
    current: RepeatRule,
    onConfirm: (RepeatRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 工作副本：用户选择基础类型时更新
    var workingRule by remember { mutableStateOf(current) }
    // customWeekdays 选集：当 workingRule 是 CustomWeekdays 时使用
    var customDays by remember {
        mutableStateOf(
            (current as? RepeatRule.CustomWeekdays)?.days ?: emptySet()
        )
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                text = "重复规则",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))

            // 基础选项列表
            BaseOptionRow(
                label = "不重复",
                selected = workingRule is RepeatRule.None,
                onClick = { workingRule = RepeatRule.None },
            )
            BaseOptionRow(
                label = "每天",
                selected = workingRule is RepeatRule.Daily,
                onClick = { workingRule = RepeatRule.Daily },
            )
            BaseOptionRow(
                label = "每周",
                selected = workingRule is RepeatRule.Weekly,
                onClick = { workingRule = RepeatRule.Weekly },
            )
            BaseOptionRow(
                label = "每月",
                selected = workingRule is RepeatRule.Monthly,
                onClick = { workingRule = RepeatRule.Monthly },
            )
            BaseOptionRow(
                label = "每年",
                selected = workingRule is RepeatRule.Yearly,
                onClick = { workingRule = RepeatRule.Yearly },
            )
            BaseOptionRow(
                label = if (customDays.isEmpty()) "自定义（请选择星期）"
                else "自定义（${customDays.size} 天）",
                selected = workingRule is RepeatRule.CustomWeekdays,
                onClick = {
                    // 切换到 Custom：把当前选集包成 RepeatRule
                    workingRule = if (customDays.isEmpty()) {
                        // 暂时给个空标记，等用户选 chip 后再补
                        RepeatRule.CustomWeekdays(setOf(DayOfWeek.MONDAY))
                    } else {
                        RepeatRule.CustomWeekdays(customDays)
                    }
                },
            )

            // 当 Custom 被选时显示 WeekdayChips
            if (workingRule is RepeatRule.CustomWeekdays || customDays.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "选择星期几",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(12.dp))
                WeekdayChips(
                    selected = customDays,
                    onToggle = { day ->
                        customDays = if (day in customDays) {
                            customDays - day
                        } else {
                            customDays + day
                        }
                        if (customDays.isNotEmpty()) {
                            workingRule = RepeatRule.CustomWeekdays(customDays)
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            // 确认按钮（Custom 模式下空选时禁用）
            val canConfirm = when {
                workingRule is RepeatRule.CustomWeekdays -> customDays.isNotEmpty()
                else -> true
            }
            androidx.compose.material3.Button(
                onClick = {
                    if (canConfirm) onConfirm(workingRule)
                },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("确定")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BaseOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
        )
        Spacer(Modifier.padding(horizontal = 12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * 一周七天 chip，单选/多选由 selected 决定。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WeekdayChips(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day in selected
            androidx.compose.material3.FilterChip(
                selected = isSelected,
                onClick = { onToggle(day) },
                label = {
                    Text(day.getDisplayName(TextStyle.SHORT, Locale.CHINA))
                },
            )
        }
    }
}
