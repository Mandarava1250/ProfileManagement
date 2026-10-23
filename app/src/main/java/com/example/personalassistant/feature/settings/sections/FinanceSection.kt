package com.example.personalassistant.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import com.example.personalassistant.feature.profile.presentation.AccountSettingsCard
import com.example.personalassistant.feature.profile.presentation.PendingAccountDeletion

/**
 * Phase 13：记账分组的长期设置
 *
 * 记账这一侧有三样长期设置：
 * - **账户**：多账户 / 当前账户 / 期初余额（原本就挂在设置页顶部）
 * - **月截止日**：账期从哪天开始切（原本埋在预算弹窗里）
 * - **分类管理** / **预算设置**：改成独立页后，从这里直接进
 */
@Composable
fun FinanceSection(
    accounts: List<UserProfileEntity>,
    currentAccountId: Long,
    pendingDelete: PendingAccountDeletion?,
    onSwitch: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDeleteRequest: (UserProfileEntity) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onAvatarClick: (UserProfileEntity) -> Unit,
    onAvatarRemove: (Long) -> Unit,
    onSetInitialBalance: (Long, Double) -> Unit,
    cutoffPolicy: MonthCutoffPolicy,
    cutoffDayText: String,
    onCutoffDayTextChange: (String) -> Unit,
    currentCycleYm: String,
    draftCutoffDay: Int?,
    onSaveCutoff: () -> Unit,
    onOpenCategory: () -> Unit,
    onOpenBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModuleHeader(
            icon = Icons.Filled.AccountBalanceWallet,
            title = "记账",
            subtitle = "账户、账期与分类",
        )

        // ===== 记账账户 =====
        AccountSettingsCard(
            accounts = accounts,
            currentAccountId = currentAccountId,
            pendingDelete = pendingDelete,
            onSwitch = onSwitch,
            onCreate = onCreate,
            onRename = onRename,
            onDeleteRequest = onDeleteRequest,
            onConfirmDelete = onConfirmDelete,
            onDismissDelete = onDismissDelete,
            onAvatarClick = onAvatarClick,
            onAvatarRemove = onAvatarRemove,
            onSetInitialBalance = onSetInitialBalance,
        )

        // ===== 月截止日（全局账期设置）=====
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("月截止日", style = MaterialTheme.typography.titleMedium)
                Spacer8()
                Text(
                    text = "记账的「一个月」从几号开始算。设为 1 就是自然月；" +
                        "设为 15 则每月 15 号到下月 14 号算一个账期。所有账户共用。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer8()

                OutlinedTextField(
                    value = cutoffDayText,
                    onValueChange = onCutoffDayTextChange,
                    label = { Text("截止日（${BillingCycle.MIN_CUTOFF_DAY}-${BillingCycle.MAX_CUTOFF_DAY}）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer8()

                // 账期区间预览：让用户立刻看到"这个月到底算哪几天"
                Text(
                    text = "当前账期：${BillingCycle.displayRange(currentCycleYm, cutoffPolicy)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (draftCutoffDay != null) {
                    Text(
                        text = "保存后：${BillingCycle.displayRange(
                            currentCycleYm,
                            MonthCutoffPolicy(
                                cutoffDay = draftCutoffDay,
                                effectiveFromYm = currentCycleYm,
                            ),
                        )}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = "修改后从当前账期开始生效，历史账期不变",
                    style = MaterialTheme.typography.bodySmall,
                )

                Spacer8()

                OutlinedButton(
                    onClick = onSaveCutoff,
                    enabled = draftCutoffDay != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存月截止日")
                }
            }
        }

        // ===== 分类管理 / 预算设置 =====
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("分类与预算", style = MaterialTheme.typography.titleMedium)
                Spacer8()
                Text(
                    text = "维护收支分类的增删改，以及每个月的预算额度。",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenCategory,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("分类管理")
                        Spacer8()
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                    OutlinedButton(
                        onClick = onOpenBudget,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("预算设置")
                        Spacer8()
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}
