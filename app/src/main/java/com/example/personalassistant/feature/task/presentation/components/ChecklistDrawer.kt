package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FilterAlt
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
import com.example.personalassistant.core.ui.components.ColorBar
import com.example.personalassistant.core.ui.components.ensureBarContrast
import com.example.personalassistant.feature.task.domain.model.ChecklistView
import com.example.personalassistant.feature.task.domain.model.NamedFilter
import com.example.personalassistant.feature.task.domain.model.SmartList
import com.example.personalassistant.feature.task.domain.model.TaskListWithCount

/**
 * 清单抽屉
 *
 * 分三段：智能清单（系统视图）/ 筛选器（用户保存的条件组合）/ 用户清单（自建归属地）。
 *
 * ## Phase 26 的改动
 *
 * - 选中态从「secondaryContainer 药丸」改成「左侧 3dp 竖条 + 品牌蓝图标 + SemiBold 文字」
 *   三重信号 —— 药丸在暖白抽屉上几乎看不出边界。
 * - 清单色点 10dp 圆点 → 3dp 竖条。
 * - 补上「删除清单」的真实入口：以前 deletingList 有状态、有对话框，
 *   却没有任何地方能给它赋值，删清单这个功能等于不存在。
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
    namedFilters: List<NamedFilter> = emptyList(),
    activeFilterName: String? = null,
    onApplyNamedFilter: ((NamedFilter) -> Unit)? = null,
    onDeleteNamedFilter: ((String) -> Unit)? = null,
    onSaveNamedFilter: ((String) -> Unit)? = null,
    /**
     * 打开习惯页。
     *
     * 用可选回调而不是直接引用 habit 模块的组件 ——
     * task 模块不反向依赖 habit，依赖方向保持单向。
     */
    onOpenHabits: (() -> Unit)? = null,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renamingList by remember { mutableStateOf<TaskListWithCount?>(null) }
    var deletingList by remember { mutableStateOf<TaskListWithCount?>(null) }
    var managingList by remember { mutableStateOf<TaskListWithCount?>(null) }
    var savingFilter by remember { mutableStateOf(false) }

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

            // 四象限（App 自有特色）—— 换排布而非筛选
            DrawerRow(
                icon = Icons.Rounded.GridView,
                label = "四象限",
                count = null,
                selected = currentView == ChecklistView.Quadrant,
                onClick = { onSelect(ChecklistView.Quadrant) },
            )

            // 习惯（独立页面，不是清单视图 —— 所以不放 selected 态）
            if (onOpenHabits != null) {
                DrawerRow(
                    icon = Icons.Rounded.DoneAll,
                    label = "习惯",
                    count = null,
                    selected = false,
                    onClick = { onOpenHabits.invoke() },
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ===== 筛选器 =====
            // 用户把一组筛选条件存起来，起个名字，随时一键套用。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "筛选器",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { savingFilter = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "保存当前筛选",
                    )
                }
            }

            if (namedFilters.isEmpty()) {
                Text(
                    text = "还没有保存的筛选器",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            } else {
                namedFilters.forEach { filter ->
                    DrawerRow(
                        icon = Icons.Rounded.FilterAlt,
                        label = filter.name,
                        count = null,
                        selected = filter.name == activeFilterName,
                        onClick = { onApplyNamedFilter?.invoke(filter) },
                        // 长按删除
                        onLongClick = { onDeleteNamedFilter?.invoke(filter.name) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ===== 用户清单 =====
            Text(
                text = "清单",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(4.dp))

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
                            managingList = entry
                        }
                    },
                )
            }

            // 新建清单入口。
            //
            // 以前这个入口是「清单」标题行右边那个「＋」图标按钮 —— 抽屉里同时挂着
            // 三个「＋」（保存筛选器 / 新建清单 / 筛选器那一行），光看形状分不清是哪一个。
            // 现在标题行只留标题，新建落到列表下方做成一行文字。
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateDialog = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "新建清单",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
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

    if (savingFilter) {
        SaveFilterDialog(
            onConfirm = { name ->
                savingFilter = false
                onSaveNamedFilter?.invoke(name)
            },
            onDismiss = { savingFilter = false },
        )
    }

    // 长按用户清单 → 管理菜单。
    //
    // 之前长按直接进重命名，「删除清单」那个对话框没有任何入口能触发，
    // 删清单这个功能等于不存在。现在两个动作都在这儿。
    managingList?.let { entry ->
        AlertDialog(
            onDismissRequest = { managingList = null },
            title = { Text("「${entry.list.displayName}」") },
            text = { Text("可以重命名这个清单，或者把它删掉。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        managingList = null
                        renamingList = entry
                    },
                ) { Text("重命名") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        managingList = null
                        deletingList = entry
                    },
                ) {
                    // 删除是破坏性操作 —— 红色是这套方案里唯一的「危险」信号。
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
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
 * 选中态 = 左侧 3dp 品牌蓝竖条 + 品牌蓝图标 + 品牌蓝 SemiBold 文字。
 * 三重信号一起给，暖白抽屉底上不会像以前那样「药丸底色糊成一片看不出选中了哪行」。
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
    val brand = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 选中指示条
        ColorBar(
            color = if (selected) brand else Color.Transparent,
            modifier = Modifier.height(20.dp),
        )
        Spacer(Modifier.width(9.dp))

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) brand else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } else {
            // 清单用 3dp 竖条代替圆点。
            // 竖条是「非文字的 UI 元素」，浅色清单色在白底上会糊掉，
            // 所以统一走 ensureBarContrast 压暗到 ≥ 3:1。
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                ColorBar(
                    color = ensureBarContrast(Color(colorHex ?: 0xFF3F7DE0L)),
                    modifier = Modifier.height(14.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) brand else MaterialTheme.colorScheme.onSurface,
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
 * 保存当前筛选为命名过滤器
 */
@Composable
private fun SaveFilterDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存当前筛选") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("筛选器名称") },
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
