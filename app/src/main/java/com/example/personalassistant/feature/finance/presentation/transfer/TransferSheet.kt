package com.example.personalassistant.feature.finance.presentation.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.feature.finance.presentation.FinanceViewModel
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * Phase 5：转账面板
 *
 * ## 为什么不复用 AddTransactionSheet
 * 那个的模型是「一个账户 + 一个类型 + 一个分类」，而转账天生需要**两个账户**、
 * 类型由方向决定（转出/转入成对写入）、分类固定是内置「转账」分类且不该让人选。
 * 硬塞进去会让类型分段按钮从 2 项变 4 项、其中 2 项点了必错。
 *
 * ## 目标账户从余额列表取
 * 用 [FinanceViewModel.accountBalances] 而不是另开一条 observeAccounts：
 * 它已经带 accountId / accountName / balance，正好满足「选的时候就能看见对方有多少钱」。
 * 少一条流也就少一个「两处账户列表不一致」的漂移面。
 *
 * ## 转出方固定是当前账户，不提供选择
 * 这是刻意的：与 [com.example.personalassistant.feature.finance.domain.repository.FinanceRepository.transfer]
 * 的签名一致（它内部用当前账户快照）。如果这里允许选转出方，用户就能在 A 的账本页面上
 * 把钱从 B 转走 —— 多一个参数就多一条越权路径，而它没有任何正向用途。
 *
 * ## Phase 9b D1 重构：状态派出去了（可测性）
 *
 * 原来 `targets / from / selected / amount / overBalance / canSubmit` 全写在本 Composable
 * 体内，只能靠 instrumented 测试碰（8m 一轮）。现在：
 * - 推导规则搬到 [TransferFormState]（纯数据类，JVM 毫秒级验证）
 * - 渲染搬到 [TransferSheetContent]（无状态，传 state + 回调即可渲染）
 * - 本函数只剩「收 VM 流 → 组装 state → 委托渲染」三件事
 *
 * 行为零变化：同一套判定条件，只是换了存放位置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val balances by viewModel.accountBalances.collectAsStateWithLifecycle()
    val currentAccountId by viewModel.currentAccountId.collectAsStateWithLifecycle()

    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf<Long?>(null) }

    val state = TransferFormState.from(
        balances = balances,
        currentAccountId = currentAccountId,
        amountText = amountText,
        note = note,
        targetId = targetId,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        TransferSheetContent(
            state = state,
            onAmountChange = { amountText = it },
            onNoteChange = { note = it },
            onTargetChange = { targetId = it },
            onSubmit = { to ->
                viewModel.transferTo(
                    toAccountId = to,
                    amountText = amountText,
                    note = note,
                    onDone = onDismiss,
                )
            },
        )
    }
}

/**
 * Phase 9b D1：转账面板的**无状态**内容（state in / callback out）。
 *
 * 与 [TransferFormState] 配对：状态由调用方给，本函数只负责画 + 抛事件。
 * instrumented 测试直接渲染它，不需要构造 [FinanceViewModel]（10+ 依赖）。
 *
 * 放在同一个文件里，是因为两者共享同一套文案与结构约定 —— 拆文件会让
 * 「改了一处忘了另一处」的概率变大，而它们的改动几乎总是同步的。
 */
@Composable
fun TransferSheetContent(
    state: TransferFormState,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onTargetChange: (Long) -> Unit,
    onSubmit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "账户间转账",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = if (state.fromName != null && state.fromBalance != null) {
                "从「${state.fromName}」转出，当前余额 ${MoneyFormat.formatPlain(state.fromBalance)}"
            } else {
                "从当前账户转出"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.hasNoTarget) {
            // 只剩一个账户：转账无处可去。说清楚原因，而不是给个空列表让人困惑
            Text(
                text = "至少需要两个账户才能转账，请到「设置 - 账户」新建一个。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Text(text = "转入账户", style = MaterialTheme.typography.titleMedium)
            state.targets.forEach { account ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { onTargetChange(account.accountId) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = account.accountName)
                    }
                    Text(
                        text = MoneyFormat.formatPlain(account.balance),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.amountText,
            onValueChange = onAmountChange,
            label = { Text("金额") },
            singleLine = true,
            isError = state.overBalance,
            supportingText = if (state.overBalance && state.fromBalance != null) {
                { Text("超出余额：当前 ${MoneyFormat.formatPlain(state.fromBalance)}") }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = { Text("备注（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.confirmSummary()?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }

        Button(
            onClick = {
                val to = state.targetId ?: return@Button
                onSubmit(to)
            },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("确认转账")
        }
    }
}