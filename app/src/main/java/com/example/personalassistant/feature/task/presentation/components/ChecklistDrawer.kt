package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.ChecklistView
import com.example.personalassistant.feature.task.domain.model.SmartList
import com.example.personalassistant.feature.task.domain.model.TaskListWithCount

/**
 * Phase 17：清单抽屉
 *
 * 滴答清单的抽屉分两段：
 * - **智能清单**：收集箱 / 今天 / 最近 7 天 / 全部 / 已完成（系统视图，不占存储）
 * - **清单**：用户自建的归属地，每行带未完成数徽标
 *
 * @param currentView 当前视图（决定哪一行高亮）
 * @param lists 用户清单 + 未完成数
 * @param onSelect 切视图
 * @param onCreateList 新建清单（名字为空则忽略）
 */
@Composable
fun ChecklistDrawer(
    currentView: ChecklistView,
    lists: List<TaskListWithCount>,
    onSelect: (ChecklistView) -> Unit,
    onCreateList: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRenameList: ((Long, String) -> Unit)? = null,
    onDeleteList: ((Long) -> Unit)? = null,
    onArchiveList: ((Long) -> Unit)? = null,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingList by remember { mutableStateOf<TaskListWithCount?>(null) }
    var deletingList by remember { mutableStateOf<TaskListWithCount?>(null) }

    ModalDrawerSheet(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // ===== 智能清单 =====
            Text(
                text = "智能清单",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))

            SmartList.entries.forEach { smart ->
                DrawerRow(
                    icon = smart.icon(),
                    label = smart.displayName(),
                    count = null,
                    selected = currentView == ChecklistView.Smart(smart),
                    onClick = { onSelect(ChecklistView.Smart(smart)) },
                )
            }

            // 四象限（App 自有特色，跟滴答无关）—— 换排布而非筛选
            DrawerRow(
                icon = Icons.Rounded.GridView,
                label = "四象限",
                count = null,
                selected = currentView == ChecklistView.Quadrant,
                onClick = { onSelect(ChecklistView.Quadrant) },
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ===== 用户清单 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "清单",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "新建清单",
                    )
                }
            }

            lists.forEach { entry ->
                DrawerRow(
                    icon = null,
                    label = entry.list.displayName,
                    count = entry.pendingCount,
                    colorHex = entry.list.colorHex,
                    selected = currentView == ChecklistView.User(entry.list.id),
                    onClick = { onSelect(ChecklistView.User(entry.list.id)) },
                    // 内置收集箱不可管理（改名 / 删除 / 归档都会破坏捕获链路）
                    onLongClick = if (entry.list.isInbox) {
                        null
                    } else {
                        {
                            renamingList = entry
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCreateDialog) {
        CreateListDialog(
            onConfirm = { name ->
                showCreateDialog = false
                if (name.isNotBlank()) onCreateList(name)
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    renamingList?.let { entry ->
        RenameListDialog(
            initialName = entry.list.displayName,
            onConfirm = { name ->
                renamingList = null
                if (name.isNotBlank()) onRenameList?.invoke(entry.list.id, name)
            },
            onDismiss = { renamingList = null },
        )
    }

    deletingList?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingList = null },
            title = { Text("删除清单「${entry.list.displayName}」？") },
            text = {
                Text(
                    if (entry.pendingCount > 0) {
                        "清单里有 ${entry.pendingCount} 个未完成任务。" +
                            "删除后这些任务会移到收集箱，不会丢失。"
                    } else {
                        "删除后无法恢复。"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deletingList = null
                        onDeleteList?.invoke(entry.list.id)
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingList = null }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 抽屉里的一行
 *
 * @param colorHex 清单色（智能清单传 null = 用主题色）
 */
@Composable
private fun DrawerRow(
    icon: ImageVector?,
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    colorHex: Long? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            // 清单用色点代替图标
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = Color(colorHex ?: 0xFFFF69B4L),
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(18.dp))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )

        if (count != null && count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 重命名清单对话框
 */
@Composable
private fun RenameListDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名清单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("清单名称") },
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 新建清单对话框
 */
@Composable
private fun CreateListDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建清单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("清单名称") },
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/** 智能清单的显示名 */
private fun SmartList.displayName(): String = when (this) {
    SmartList.INBOX -> "收集箱"
    SmartList.TODAY -> "今天"
    SmartList.TOMORROW -> "明天"
    SmartList.NEXT_7_DAYS -> "最近 7 天"
    SmartList.ALL -> "全部"
    SmartList.COMPLETED -> "已完成"
}

/** 智能清单的图标 */
private fun SmartList.icon(): ImageVector = when (this) {
    SmartList.INBOX -> Icons.Rounded.Inbox
    SmartList.TODAY -> Icons.Rounded.Today
    SmartList.TOMORROW -> Icons.Rounded.CalendarMonth
    SmartList.NEXT_7_DAYS -> Icons.Rounded.CalendarMonth
    SmartList.ALL -> Icons.Rounded.ListAlt
    SmartList.COMPLETED -> Icons.Rounded.DoneAll
}
