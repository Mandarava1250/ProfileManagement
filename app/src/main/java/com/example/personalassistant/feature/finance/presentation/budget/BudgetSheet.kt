package com.example.personalassistant.feature.finance.presentation.budget

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
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
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode

/**
 * C 重构阶段 3：月度预算 Sheet
 *
 * - B 修复：原 [LaunchedEffect(persisted)] 每次 persisted 流发值都会覆盖 _editing，
 *   用户正在改的草稿会被吞掉；现在只在**首次**拉到值时加载一次。
 * - C：预算编辑字段下方插入 [MonthPickerBar]，点击中部文本会浮 [YearMonthPickerSheet]。
 * - Phase 13：内容抽到 [BudgetForm]，让预算既能以底部弹窗出现（记账页），
 *   也能以独立页面出现（设置页的「记账」分组入口）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSheet(
    onDismiss: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BudgetForm(
            viewModel = viewModel,
            onSaveDone = onDismiss,
        )
    }
}

/**
 * 预算表单内容（不含外层容器）。
 *
 * [BudgetSheet]（底部弹窗）与 [BudgetScreen]（独立页面）共用这一份，
 * 保证两处看到的字段、校验、保存行为完全一致。
 */
@Composable
fun BudgetForm(
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel(),
    /** 标题；独立页面有自己的 TopAppBar 时传 null */
    title: String? = "月度预算",
    /** 保存成功后回调（弹窗用来关闭自己；独立页面用来 popBackStack） */
    onSaveDone: () -> Unit = {},
) {
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val persisted by viewModel.persistedBudget.collectAsStateWithLifecycle()
    // C：当前选中的月份（来自 VM）
    val yearMonth by viewModel.yearMonth.collectAsStateWithLifecycle()
    // 月截止日策略（全局设置）—— 只读，写入在设置页
    val cutoffPolicy by viewModel.cutoffPolicy.collectAsStateWithLifecycle()
    // C：是否显示年份选择 Sheet
    var showYearMonthPicker by remember { mutableStateOf(false) }
    // 批次 C：「今日剩余」显示口径
    val todayRemainingMode by viewModel.todayRemainingMode.collectAsStateWithLifecycle()

    // B 修复：只在**首次**有 persisted 时加载；后续 persisted 流更新不再覆盖 _editing
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(persisted) {
        if (!loaded && persisted != null) {
            viewModel.loadFromPersisted(persisted)
            loaded = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        }

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

        // ===== 批次 C：「今日剩余」显示口径 =====
        //
        // 放在预算弹窗而不是记账页顶栏：这是一条**显示偏好**，
        // 和「每天自动刷新零钱池」「昨日剩余结转今天」是同一类东西，放一起好找。
        Text(
            text = "「今日剩余」显示方式",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TodayRemainingMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { viewModel.setTodayRemainingMode(mode) },
                ) {
                    RadioButton(
                        selected = todayRemainingMode == mode,
                        onClick = { viewModel.setTodayRemainingMode(mode) },
                    )
                    Text(
                        text = when (mode) {
                            TodayRemainingMode.FIXED -> "固定（本月还能花多少）"
                            TodayRemainingMode.AUTO -> "自动（按剩余天数均摊）"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // ===== 月截止日（全局设置，所有月份共用）=====
        //
        // Phase 13：截止日输入框搬到「设置 → 记账 → 月截止日」了。
        // 这里只保留一句指路 —— 账期区间本身由 MonthPickerBar 的副标题展示。
        Text(
            text = "账期区间由「设置 → 记账 → 月截止日」决定",
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
            onClick = { viewModel.save(onSaveDone) },
            enabled = editing.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存预算") }
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
