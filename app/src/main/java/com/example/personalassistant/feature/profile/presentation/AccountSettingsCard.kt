package com.example.personalassistant.feature.profile.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity

/**
 * Phase 3：账户管理卡片（挂在 Settings 页顶部）
 *
 * 承载：账户列表 + 当前账户勾选 + 新建 / 重命名 / 切换 / 删除。
 *
 * ## 为什么走 Settings 而不是第 5 个底部 Tab
 * 账户是低频管理动作（建好之后主要在记账页顶部切换），
 * 塞进 BottomTab 会让 4 个 Tab 变 5 个、挤压记账页横向空间；
 * Settings 页已有 3 个 Card 分区，多一个「记账账户」正是它该待的地方。
 *
 * ## 删除的两道保护
 * - 仅剩 1 个账户 → 删除按钮禁用（[canDelete]=false）
 * - 有流水 → 由 [onDeleteRequest] 触发 ViewModel 查流水数，再由 [pendingDelete] 弹二次确认
 *
 * 文案与 SettingsScreen 现状一致（硬编码中文），不引 stringResource，
 * 避免同一页出现两种文案风格。
 */
@Composable
fun AccountSettingsCard(
    accounts: List<UserProfileEntity>,
    currentAccountId: Long,
    pendingDelete: PendingAccountDeletion?,
    onSwitch: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDeleteRequest: (UserProfileEntity) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    /** 正在重命名的账户（本地 UI 状态，不进 ViewModel） */
    var renamingAccount by remember { mutableStateOf<UserProfileEntity?>(null) }
    /** 新建对话框 */
    var showCreateDialog by remember { mutableStateOf(false) }

    val canDelete = accounts.size > 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null)
                IconTitleGap()
                Text("记账账户", style = MaterialTheme.typography.titleMedium)
            }
            IconTitleGap()
            Text(
                text = "每个账户一本独立账本：流水、分类、月预算、零钱池互不干扰。" +
                    "任务 / 日历 / 课表不受影响，仍然全局共享。",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            accounts.forEach { account ->
                AccountRow(
                    account = account,
                    isCurrent = account.id == currentAccountId,
                    canDelete = canDelete,
                    onSwitch = { onSwitch(account.id) },
                    onRename = { renamingAccount = account },
                    onDelete = { onDeleteRequest(account) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                IconTitleGap()
                Text("新建账户")
            }
        }
    }

    // ===== 新建账户 =====
    if (showCreateDialog) {
        AccountNameDialog(
            title = "新建账户",
            initialValue = "",
            confirmLabel = "创建",
            onConfirm = { name ->
                onCreate(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    // ===== 重命名 =====
    renamingAccount?.let { target ->
        AccountNameDialog(
            title = "重命名账户",
            initialValue = target.name,
            confirmLabel = "保存",
            onConfirm = { name ->
                onRename(target.id, name)
                renamingAccount = null
            },
            onDismiss = { renamingAccount = null },
        )
    }

    // ===== 删除二次确认（流水数由 ViewModel 查好后回填） =====
    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
            title = { Text("删除账户「${pending.name}」？") },
            text = {
                Text(
                    if (pending.transactionCount > 0) {
                        "该账户下有 ${pending.transactionCount} 笔流水，" +
                            "以及它自己的分类、月预算与零钱池。删除后不可恢复，" +
                            "建议先做一次数据备份。"
                    } else {
                        "该账户暂无流水。删除后不可恢复。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text("取消") }
            },
        )
    }
}

@Composable
private fun AccountRow(
    account: UserProfileEntity,
    isCurrent: Boolean,
    canDelete: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrent, onClick = onSwitch)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(selected = isCurrent, onClick = onSwitch)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            )
            if (isCurrent) {
                Text("当前使用中", style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Filled.Edit, contentDescription = "重命名", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = if (canDelete) "删除账户" else "至少保留一个账户，无法删除",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 输入账户名的对话框（新建与重命名共用） */
@Composable
private fun AccountNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialValue) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (trimmed.isEmpty()) {
                    IconTitleGap()
                    Text(
                        "名称不能为空",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 待确认的删除请求：名字 + 该账户流水数（-1 表示尚未查到） */
data class PendingAccountDeletion(
    val accountId: Long,
    val name: String,
    val transactionCount: Int,
)

@Composable
private fun IconTitleGap() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
}
