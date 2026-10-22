package com.example.personalassistant.feature.finance.presentation.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.feature.finance.domain.BillingCycle

/**
 * C 重构阶段 3：月度预算 Sheet
 *
 * - B 修复：原 [LaunchedEffect(persisted)] 每次 persisted 流发值都会覆盖 _editing，
 *   用户正在改的草稿会被吞掉；现在只在**首次**拉到值时加载一次。
 * - C：预算编辑字段下方插入 [MonthPickerBar]，点击中部文本会浮 [YearMonthPickerSheet]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSheet(
    onDismiss: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val persisted by viewModel.persistedBudget.collectAsStateWithLifecycle()
    // C：当前选中的月份（来自 VM）
    val yearMonth by viewModel.yearMonth.collectAsStateWithLifecycle()
    // 月截止日策略（全局设置）
    val cutoffPolicy by viewModel.cutoffPolicy.collectAsStateWithLifecycle()
    // C：是否显示年份选择 Sheet
    var showYearMonthPicker by remember { mutableStateOf(false) }

    // B 修复：只在**首次**有 persisted 时加载；后续 persisted 流更新不再覆盖 _editing
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(persisted) {
        if (!loaded && persisted != null) {
            viewModel.loadFromPersisted(persisted)
            loaded = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "月度预算",
                style = MaterialTheme.typography.titleLarge,
            )

            // C：月份选择器
            MonthPickerBar(
                yearMonth = yearMonth,
                onPrev = { viewModel.goPrevMonth() },
                onNext = { viewModel.goNextMonth() },
                onTapYearMonth = { showYearMonthPicker = true },
                subtitle = BillingCycle.displayRange(yearMonth, cutoffPolicy),
            )

            OutlinedTextField(
                value = editing.totalAmountText,
                onValueChange = viewModel::setAmount,
                label = { Text("本月可用金额") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = editing.warningThresholdText,
                onValueChange = viewModel::setWarningThreshold,
                label = { Text("警告阈值（剩余低于此值时变橙）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // ===== 月截止日（全局设置，所有月份共用）=====
            OutlinedTextField(
                value = editing.cutoffDayText,
                onValueChange = viewModel::setCutoffDay,
                label = { Text("月截止日（1-28，留空不改）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            // 账期区间预览：让用户立刻看到"这个月到底算哪几天"
            Text(
                text = "当前账期：${BillingCycle.displayRange(yearMonth, cutoffPolicy)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "修改后从当前账期开始生效，历史账期不变",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("每天自动刷新零钱池", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = editing.isAutoRefresh,
                    onCheckedChange = viewModel::setAutoRefresh,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("昨日剩余结转今天", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = editing.carryOverEnabled,
                    onCheckedChange = viewModel::setCarryOverEnabled,
                )
            }

            Button(
                onClick = { viewModel.save(onDismiss) },
                enabled = editing.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存预算") }
        }
    }

    // C：弹层选历史月份
    if (showYearMonthPicker) {
        YearMonthPickerSheet(
            currentYearMonth = yearMonth,
            availableMonths = viewModel.allBudgetYearMonths.collectAsStateWithLifecycle().value,
            onPick = { picked ->
                viewModel.setYearMonth(picked)
                showYearMonthPicker = false
            },
            onDismiss = { showYearMonthPicker = false },
        )
    }
}
