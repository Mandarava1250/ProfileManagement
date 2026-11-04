package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.personalassistant.core.ui.components.AppCard
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.finance.domain.model.CoinPool
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * 批次 C：零钱池卡片（与「今日剩余」卡平级）
 *
 * ## 为什么必须和「今日剩余」卡区分开
 *
 * 两张卡都是「钱」，但语义完全相反：
 * - 「今日剩余」= 预算系统告诉你**今天还能花多少**（系统给的额度，会随记账变）
 * - 「零钱池」= 你**自己**攒的一笔零用钱（手动存取，跟预算无关）
 *
 * 用户看到两个「¥」如果分不清，就会以为「零钱池 = 今日剩余」，
 * 存了一笔进去发现「今日剩余」没变 —— 那是错账的观感。
 * 所以：标题不同（「零钱池」vs「今日剩余」）、图标不同（储蓄罐 vs 无）、
 * 副标题明说「与今日剩余无关」、卡片底色不同（本卡用 surface，「今日剩余」用 primaryContainer）。
 *
 * ## 负余额
 *
 * 余额可以为负（欠账），显示为红色（[LocalAppColors] 的 expenseInk）。
 */
@Composable
fun CoinPoolCard(
    pool: CoinPool?,
    onAdjust: (delta: Double, note: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appColors = LocalAppColors.current
    // null = 关闭；1 = 存入；2 = 取出
    var dialogMode by remember { mutableStateOf<Int?>(null) }

    AppCard(
        modifier = modifier.fillMaxWidth(),
        accent = appColors.budgetInk,
        contentPadding = PaddingValues(AppSpacing.lg),
    ) {
        // ===== 标题行：图标 + 「零钱池」=====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "零钱池",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ===== 余额 =====
        // pool 为 null 表示「还没建零钱池」，与「余额是 0」是两回事，
        // 所以这里分开处理：null 走占位文案，非 null 才谈正负。
        val balance = pool?.balance ?: 0.0
        Text(
            text = if (pool == null) "还没建零钱池" else MoneyFormat.format(balance),
            style = MaterialTheme.typography.headlineMedium,
            color = when {
                pool == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                balance < 0 -> appColors.expenseInk          // 红：欠账
                else -> MaterialTheme.colorScheme.onSurface
            },
        )

        // ===== 副标题：备注 / 说明 =====
        Text(
            text = pool?.note?.takeIf { it.isNotBlank() }
                ?: "随时存取的小金库，与「今日剩余」无关",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ===== 存 / 取 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            Button(onClick = { dialogMode = 1 }) {
                Text("存入")
            }
            OutlinedButton(onClick = { dialogMode = 2 }) {
                Text("取出")
            }
        }
    }

    if (dialogMode != null) {
        CoinPoolEditDialog(
            isDeposit = dialogMode == 1,
            initialNote = pool?.note ?: "",
            onDismiss = { dialogMode = null },
            onConfirm = { amount, note ->
                val delta = if (dialogMode == 1) amount else -amount
                onAdjust(delta, note)
                dialogMode = null
            },
        )
    }
}

/**
 * 存取金额输入弹窗
 *
 * 金额必须 > 0（存0 元 / 取 0 元没有意义）；
 * 余额允许为负 —— 那是「取多了」，在卡片上显示红色，由用户自己决定要不要继续。
 */
@Composable
private fun CoinPoolEditDialog(
    isDeposit: Boolean,
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (Double, String?) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf(initialNote) }
    val amount = amountText.trim().toDoubleOrNull()
    val valid = amount != null && amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDeposit) "存入零钱池" else "取出零钱池") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            val v = amount
            TextButton(
                onClick = {
                    if (v != null) {
                        onConfirm(v, noteText.trim().takeIf { it.isNotEmpty() })
                    }
                },
                enabled = valid,
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
