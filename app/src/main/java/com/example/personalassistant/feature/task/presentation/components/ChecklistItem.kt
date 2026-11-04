package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.AppMetaChip
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.TaskCategory
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 清单项（独立清单 / 任务详情子清单 / 编辑页子清单 共用）
 *
 * ## Phase 15：展示增强 —— 全部走**可选参数**，默认关闭
 *
 * 老调用方（任务详情页的 `SubTaskSection`）只传前三个参数，行为与以前**完全一致**：
 * 分类 / 截止时间 / 优先级 / 金额 / 标签 / 编辑入口都不显示。
 * 独立清单页（`ChecklistTab`）才会把这些参数打开。
 *
 * @param item 清单项本体（Domain 模型）
 * @param onToggleComplete 勾选回调
 * @param onDelete 删除回调
 * @param category 分类名（null = 不显示分类色点）
 * @param dueDate 截止时间（毫秒时间戳，null = 不显示）
 * @param priority 优先级（null = 不显示优先级标识）
 * @param amount 金额（null = 不显示）
 * @param tags 标签（空列表 = 不显示标签 chip）
 * @param onEdit 编辑入口（null = 不显示编辑按钮）
 * @param onLongPress 长按回调（null = 不启用长按手势）
 * @param onTagClick 点标签 chip 的回调（null = 点了没反应）
 */
@Composable
fun ChecklistItem(
    item: Task,
    onToggleComplete: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    // ===== Phase 15 新增（全部可选，默认关闭 → 老调用方零影响）=====
    category: String? = null,
    dueDate: Long? = null,
    priority: TaskPriority? = null,
    amount: Double? = null,
    tags: List<Tag> = emptyList(),
    onEdit: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onTagClick: ((Long) -> Unit)? = null,
) {
    ChecklistItemRow(
        title = item.title,
        isCompleted = item.isCompleted,
        onToggleComplete = onToggleComplete,
        onDelete = onDelete,
        modifier = modifier,
        category = category,
        dueDate = dueDate,
        priority = priority,
        amount = amount,
        tags = tags,
        onEdit = onEdit,
        onLongPress = onLongPress,
        onTagClick = onTagClick,
    )
}

/**
 * 清单项的**公共行** —— 独立清单页的卡片与旧的 [ChecklistItem] 共用同一套画法。
 *
 * 结构：勾选框 + （标题 / 元信息）+ 尾部操作按钮。
 *
 * @param gestureKey 长按手势的 key（一般传清单项 id）—— 变了才重启手势检测，
 *                    避免每次重组都把长按打断
 * @param selectionMode Phase 18.6：多选模式（左侧勾选框变成「选中」框，尾部按钮隐藏）
 * @param selected 多选模式下该项是否被选中
 * @param onToggleSelect 多选模式下点勾选框的回调
 */
@Composable
internal fun ChecklistItemRow(
    title: String,
    isCompleted: Boolean,
    onToggleComplete: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    category: String? = null,
    dueDate: Long? = null,
    priority: TaskPriority? = null,
    amount: Double? = null,
    tags: List<Tag> = emptyList(),
    onEdit: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onTagClick: ((Long) -> Unit)? = null,
    gestureKey: Any? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    // ===== Phase 18.6：多选 =====
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
) {
    // rememberUpdatedState：手势检测只在 gestureKey 变化时重启，
    // 但回调要永远拿到最新的一份，否则长按会调到旧数据。
    val currentLongPress by rememberUpdatedState(onLongPress)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Phase 25：换成 AppSpacing token —— 页面其它区块走的是这套刻度，
            // 这里硬编码 12.dp / 4.dp 会让卡片内边距跟区块间距对不上。
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                // 多选模式：勾选框变成「选中」框
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect?.invoke() },
                )
            } else {
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = onToggleComplete,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = if (currentLongPress != null) {
                        Modifier.pointerInput(gestureKey) {
                            detectTapGestures(onLongPress = { currentLongPress?.invoke() })
                        }
                    } else {
                        Modifier
                    },
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        color = if (isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        // Phase 25：标题单行（滴答的风格）。两行标题会把行高撑开，
                        // 列表扫视时节奏很乱；多出来的信息下沉到下面的 meta 区（FlowRow 会自动换行）。
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ChecklistMetaRow(
                    category = category,
                    dueDate = dueDate,
                    priority = priority,
                    amount = amount,
                    tags = tags,
                    onTagClick = onTagClick,
                )
            }
            // 多选模式：编辑 / 删除按钮让位给「选中」语义
            if (!selectionMode) {
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.checklist_item_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Phase 25：间隔只在「编辑」按钮真的存在时才有 ——
                    // 没有编辑按钮时这个 Spacer 是一段莫名其妙的空隙。
                    Spacer(Modifier.width(AppSpacing.xxs))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        trailingContent?.invoke()
    }
}

/**
 * 清单项下方的元信息行：分类色点 / 截止时间 / 金额 / 优先级 / 标签。
 *
 * 一个都没有时整行不渲染 —— 这样老的 [ChecklistItem] 不会多出空行。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChecklistMetaRow(
    category: String?,
    dueDate: Long?,
    priority: TaskPriority?,
    amount: Double?,
    tags: List<Tag>,
    onTagClick: ((Long) -> Unit)?,
) {
    val hasMeta = category != null || dueDate != null || priority != null ||
        amount != null || tags.isNotEmpty()
    if (!hasMeta) return

    // Phase 25：4.dp / 2.dp 换成 AppSpacing token —— 跟同一文件里
    // 紧邻的 AppSpacing.xxs 混用会让 meta 区的缩进看起来忽宽忽窄。
    Column(modifier = Modifier.padding(start = AppSpacing.xxs, top = AppSpacing.xxs)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            if (category != null) {
                CategoryLabel(category = category)
            }
            if (dueDate != null) {
                val overdue = isOverdue(dueDate)
                AppMetaChip(
                    text = dueDateLabel(dueDate),
                    containerColor = if (overdue) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (overdue) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (amount != null) {
                AppMetaChip(text = stringResource(R.string.checklist_amount, formatAmountNumber(amount)))
            }
            if (priority != null) {
                AppMetaChip(
                    text = priority.label,
                    contentColor = priorityInk(priority),
                )
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                modifier = Modifier.padding(top = AppSpacing.xxs),
            ) {
                tags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = false,
                        onClick = { onTagClick?.invoke(tag.id) },
                    )
                }
            }
        }
    }
}

/** 分类色点 + 分类名（色值取自 [TaskCategory.PRESETS]）。 */
@Composable
private fun CategoryLabel(category: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(categoryDotColor(category)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = category,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 分类色：命中预设用预设色，否则用中性描边色。
 *
 * 预设里的 colorHex 是 "#RRGGBB" **文本** —— Compose 1.6 的 [Color] 只有
 * Int / Long 重载、**没有 String 重载**，所以先自己解析成 ARGB Int 再喂给 [Color]。
 * 万一数据里有脏值也不能让整页崩 —— 解析失败退回中性色。
 */
@Composable
internal fun categoryDotColor(category: String): Color {
    val preset = TaskCategory.PRESETS.firstOrNull { it.name == category }
    val argb = preset?.let { parseArgbHex(it.colorHex) }
    return argb?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
}

/**
 * 把 "#RRGGBB" / "#AARRGGBB"（"#" 可省）解析成 ARGB Int。
 *
 * 长度不对 / 含非 hex 字符一律返回 null，由调用方退回中性色。
 * 纯 Kotlin 实现，不碰 android.graphics —— JVM 单元测试也能跑。
 */
private fun parseArgbHex(hex: String): Int? {
    val raw = hex.trim().removePrefix("#")
    if (raw.length != 6 && raw.length != 8) return null
    val value = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) {
        // 6 位没带 alpha，按不透明处理
        (0xFF000000L or value).toInt()
    } else {
        value.toInt()
    }
}

/** 优先级语义色（与任务卡 `TaskItem` 同一套 token）。 */
@Composable
internal fun priorityInk(priority: TaskPriority): Color {
    val appColors = LocalAppColors.current
    return when (priority) {
        TaskPriority.URGENT, TaskPriority.HIGH -> appColors.priorityHighInk
        TaskPriority.MEDIUM -> appColors.priorityMediumInk
        TaskPriority.NORMAL -> appColors.priorityLowInk
        TaskPriority.LOW -> MaterialTheme.colorScheme.outline
    }
}

/** 截止时间的可读文案（今天 / 明天 / 昨天 / 月日 / 年月日）。 */
@Composable
internal fun dueDateLabel(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> stringResource(R.string.checklist_due_today)
        today.plusDays(1) -> stringResource(R.string.checklist_due_tomorrow)
        today.minusDays(1) -> stringResource(R.string.checklist_due_yesterday)
        else -> if (date.year == today.year) {
            "${date.monthValue}/${date.dayOfMonth}"
        } else {
            "${date.year}/${date.monthValue}/${date.dayOfMonth}"
        }
    }
}

/** 是否已逾期（按「天」判定：截止日期早于今天就算逾期）。 */
internal fun isOverdue(millis: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val due = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return due.isBefore(LocalDate.now(zone))
}

/** 金额格式化：固定两位小数，不受系统 Locale 影响（避免小数点变成逗号）。 */
internal fun formatAmountNumber(amount: Double): String {
    val scaled = Math.round(amount * 100.0)
    val sign = if (scaled < 0) "-" else ""
    val abs = Math.abs(scaled).toLong()
    return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}
