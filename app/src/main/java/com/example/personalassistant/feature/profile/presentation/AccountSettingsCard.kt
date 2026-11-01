package com.example.personalassistant.feature.profile.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
    onAvatarClick: (UserProfileEntity) -> Unit,
    onAvatarRemove: (Long) -> Unit,
    /**
     * Phase 5：设置期初余额（余额是派生值，这里只改「期初」那一项）。
     * 默认空实现：本卡片被预览/测试单独调用时不必强制接线，但 Settings 页必须传，
     * 否则新账户永远转不出钱（余额 0 会被转账校验拦住）。
     */
    onSetInitialBalance: (Long, Double) -> Unit = { _, _ -> },
) {
    /** 正在重命名的账户（本地 UI 状态，不进 ViewModel） */
    var renamingAccount by remember { mutableStateOf<UserProfileEntity?>(null) }
    /** 新建对话框 */
    var showCreateDialog by remember { mutableStateOf(false) }
    /** Phase 5：正在设期初余额的账户 */
    var balanceAccount by remember { mutableStateOf<UserProfileEntity?>(null) }

    val canDelete = accounts.size > 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, contentDescription = null)
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
                    onAvatarClick = { onAvatarClick(account) },
                    onAvatarRemove = { onAvatarRemove(account.id) },
                    onBalanceClick = { balanceAccount = account },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            OutlinedButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
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

    // ===== Phase 5：期初余额 =====
    balanceAccount?.let { target ->
        InitialBalanceDialog(
            accountName = target.name,
            initialValue = target.initialBalance,
            onConfirm = { balance ->
                onSetInitialBalance(target.id, balance)
                balanceAccount = null
            },
            onDismiss = { balanceAccount = null },
        )
    }

    // ===== 删除二次确认（流水数由 ViewModel 查好后回填） =====
    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
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
    onAvatarClick: () -> Unit,
    onAvatarRemove: () -> Unit,
    /** Phase 5：打开「设为期初余额」对话框 */
    onBalanceClick: () -> Unit,
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
        // Phase 4：头像**只展示不可点**。
        // 整行已绑了 clickable(onSwitch)，头像若也可点会先误触「切换账户」，
        // 所以换头像走右侧独立的相机 IconButton（不嵌在行点击区里）。
        AccountAvatar(
            account = account,
            onAvatarRemove = onAvatarRemove,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            )
            // Phase 5：期初余额直接标在副行 —— 余额本身是派生值，
            // 而这里拿得到的是期初；不写清楚的话用户会误以为这就是当前余额。
            // 本模块自己格式化（不拆 finance 的 MoneyFormat）：profile 表现层不该依赖另一个 feature 的表现层
            val initialNote = if (account.initialBalance == 0.0) {
                null
            } else {
                "期初 ${formatAmount(account.initialBalance)}"
            }
            when {
                isCurrent && initialNote != null -> Text(
                    "当前使用中 · $initialNote",
                    style = MaterialTheme.typography.bodySmall,
                )
                isCurrent -> Text("当前使用中", style = MaterialTheme.typography.bodySmall)
                initialNote != null -> Text(
                    initialNote,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        IconButton(onClick = onBalanceClick) {
            Icon(
                Icons.Rounded.AttachMoney,
                contentDescription = "设置期初余额",
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onAvatarClick) {
            Icon(
                Icons.Rounded.PhotoCamera,
                contentDescription = "更换头像",
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Rounded.Edit, contentDescription = "重命名", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = if (canDelete) "删除账户" else "至少保留一个账户，无法删除",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Phase 4：账户头像圆形缩略图（36dp）
 *
 * - 无头像时用 `Icons.Rounded.Person` 占位，保证行高一致
 * - **本身不可点**（原因见 [AccountRow] 内注释）；有头像时右下角叠一个小的「移除」按钮，
 *   那是独立 IconButton，会自己消费点击，不会泄到整行的 onSwitch
 * - Coil 已在项里（Phase 2 附件缩略图），2.7.0，无需加依赖
 */
@Composable
private fun AccountAvatar(
    account: UserProfileEntity,
    onAvatarRemove: () -> Unit = {},
) {
    val hasAvatar = !account.avatarUri.isNullOrBlank()

    Box {
        if (hasAvatar) {
            AsyncImage(
                model = account.avatarUri,
                contentDescription = "${account.name}的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = "未设置头像",
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        if (hasAvatar) {
            IconButton(
                onClick = onAvatarRemove,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "移除头像",
                    modifier = Modifier.size(14.dp),
                )
            }
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

/** 待确认的删除请求：名字 + 该账户流水数（-1 表示尚未查到） */data class PendingAccountDeletion(
    val accountId: Long,
    val name: String,
    val transactionCount: Int,
)

@Composable
private fun IconTitleGap() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
}

/** 金额文本：最多 2 位小数，整数不补零（账户名行与期初余额对话框共用） */
private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)

/**
 * Phase 5：设置期初余额。
 *
 * 与账户名对话框分开而不共用：两者语义完全不同（一个是文本，一个是金额），
 * 共用一个带 mode 参数的对话框会把校验逻辑搅在一起。
 *
 * 允许负数（信用卡/负债类账户期初就是负的）；但不允许非数字，
 * 因为 NaN/Infinity 写进 REAL 列后余额比较会默默出错（Repository 也有一层 require 守卫）。
 */
@Composable
private fun InitialBalanceDialog(
    accountName: String,
    initialValue: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember {
        mutableStateOf(if (initialValue == 0.0) "" else formatAmount(initialValue))
    }
    val parsed = text.trim().toDoubleOrNull()
    // 空串按 0 处理（让「清除期初」有个自然入口），但非法文本必须拦住
    val isValid = text.isBlank() || (parsed != null && parsed.isFinite())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("期初余额") },
        text = {
            Column {
                Text(
                    text = "「$accountName」开始记账前已有的金额，可为负。" +
                        "当前余额 = 期初 + 收入 − 支出 + 转入 − 转出，会随之重算。",
                    style = MaterialTheme.typography.bodySmall,
                )
                IconTitleGap()
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("期初余额（留空计为 0）") },
                    singleLine = true,
                    isError = !isValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!isValid) {
                    IconTitleGap()
                    Text(
                        "请输入数字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim().toDoubleOrNull() ?: 0.0) },
                enabled = isValid,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
