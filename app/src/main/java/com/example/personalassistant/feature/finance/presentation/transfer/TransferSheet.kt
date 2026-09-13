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

    // 排掉当前账户：自己转给自己在 Repository 层会被拒（SELF_TRANSFER），
    // 但把不可能选项摆在眼前仍然更差 —— 用户点了才知道不行，等于让人试错。
    val targets = balances.filter { it.accountId != currentAccountId }
    val from = balances.firstOrNull { it.accountId == currentAccountId }
    val selected = targets.firstOrNull { it.accountId == targetId }
    val amount = amountText.trim().toDoubleOrNull()
    // 余额不足在 UI 层就提示（Repository 仍会再校验一次，那是真正的防线）：
    // 这里只是提前把「转不出去」说清楚，避免用户填完才发现。
    val overBalance = amount != null && amount > 0.0 && from != null && from.balance < amount
    val canSubmit = amount != null && amount > 0.0 && targetId != null && !overBalance

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
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
                text = if (from != null) {
                    "从「${from.accountName}」转出，当前余额 ${MoneyFormat.formatPlain(from.balance)}"
                } else {
                    "从当前账户转出"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (targets.isEmpty()) {
                // 只剩一个账户：转账无处可去。说清楚原因，而不是给个空列表让人困惑
                Text(
                    text = "至少需要两个账户才能转账，请到「设置 - 账户」新建一个。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(text = "转入账户", style = MaterialTheme.typography.titleMedium)
                targets.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { targetId = account.accountId },
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
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("金额") },
                singleLine = true,
                isError = overBalance,
                supportingText = if (overBalance && from != null) {
                    { Text("超出余额：当前 ${MoneyFormat.formatPlain(from.balance)}") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            selected?.let {
                Text(
                    text = "确认后：${from?.accountName ?: "当前账户"} −${amountText.trim()} → ${it.accountName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Button(
                onClick = {
                    val to = targetId ?: return@Button
                    viewModel.transferTo(
                        toAccountId = to,
                        amountText = amountText,
                        note = note,
                        onDone = onDismiss,
                    )
                },
                enabled = targets.isNotEmpty() && canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("确认转账")
            }
        }
    }
}
