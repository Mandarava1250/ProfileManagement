package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.ColorBar
import com.example.personalassistant.core.ui.components.RingCheckbox
import com.example.personalassistant.core.ui.components.ensureBarContrast
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.model.TaskCategory
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.ui.text.style.TextDecoration

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
            // Phase 26：左右 20dp —— 大标题 / 分组标题 / meta 都用这一档，
            // 行的左缘跟它们对齐，整页才是一条竖线。
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
    ) {
        // 顶部对齐：圆环要跟**标题**齐平，而不是跟「标题 + meta」这一整块居中。
        Row(verticalAlignment = Alignment.Top) {
            // 圆环勾选框 —— 自带 28dp 布局槽（视觉圆环 + 右侧留白），不要再自己加 padding。
            // 多选模式下同一个圆环表达「已选中」，不再另画一个方框。
            RingCheckbox(
                checked = if (selectionMode) selected else isCompleted,
                onCheckedChange = {
                    if (selectionMode) onToggleSelect?.invoke() else onToggleComplete(it)
                },
            )
            Column(modifier = Modifier.weight(1f)) {
                // 标题行：标题 + 行尾折叠箭头（trailingContent）。
                // 折叠箭头以前是整条满宽的独立行，视觉上像另一条内容，
                // 现在收进标题行末尾，跟标题一起被省略号约束。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (currentLongPress != null) {
                                    Modifier.pointerInput(gestureKey) {
                                        detectTapGestures(onLongPress = { currentLongPress?.invoke() })
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCompleted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            // Phase 25：标题单行（滴答的风格）。两行标题会把行高撑开，
                            // 列表扫视时节奏很乱；多出来的信息下沉到下面的 meta 区（FlowRow 会自动换行）。
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 已完成态划一道删除线。
                            //
                            // 之前这里自己按 TextLayoutResult 的行度量画线，想让删除线
                            // 用分隔线色、比文字浅 —— 但当前 Compose 版本的 TextLayoutResult
                            // 上取不到行基线与行宽，编译不过。
                            // 改回 TextDecoration.LineThrough：删除线跟着文字色走，
                            // 跟日历页、任务详情页、清单编辑页的画法完全一致。
                            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    trailingContent?.invoke()
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
    }
}

/**
 * 清单项下方的元信息行：分类竖条 / 优先级 / 截止时间 / 金额 / 标签。
 *
 * Phase 26：**全是纯文字，没有底色胶囊**。
 * 之前每条信息都套一个 `surfaceVariant` 药丸，一行里挤着四个灰底小块，
 * 颜色种类多、视觉噪音大；这套方案里「色只做标识」，信息靠文字本身。
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

    val appColors = LocalAppColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // meta 区跟标题左缘对齐：它跟标题在同一个 Column 里，
    // 所以这里**不能再加左缩进**（之前多缩进 4dp，色点跟标题不在一条竖线上）。
    Column(modifier = Modifier.padding(top = AppSpacing.xxs)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        ) {
            if (category != null) {
                CategoryLabel(category = category)
            }
            if (priority != null) {
                MetaText(
                    text = priority.label,
                    color = priorityInk(priority),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (dueDate != null) {
                val overdue = isOverdue(dueDate)
                MetaText(
                    text = dueDateLabel(dueDate),
                    // 逾期是唯一需要「喊一下」的信息 —— 用支出语义色（固定色，不跟主题色走）
                    color = if (overdue) appColors.expenseInk else muted,
                    fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (amount != null) {
                MetaText(
                    text = stringResource(R.string.checklist_amount, formatAmountNumber(amount)),
                    color = muted,
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

/** meta 区里的一小段纯文字（无底色）。 */
@Composable
private fun MetaText(
    text: String,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
    )
}

/**
 * 分类竖条 + 分类名（色值取自 [TaskCategory.PRESETS]）。
 *
 * Phase 26：10dp 圆点改成 3dp 竖条 —— 色只做标识，不再占一块圆饼。
 * 竖条是「非文字的 UI 元素」，浅色分类在白底上对比度不够，
 * 所以走 [ensureBarContrast] 压暗到 ≥ 3:1 再画。
 */
@Composable
private fun CategoryLabel(category: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorBar(
            color = ensureBarContrast(categoryBarColor(category)),
            modifier = Modifier.height(11.dp),
        )
        Spacer(Modifier.width(AppSpacing.xxs))
        Text(
            text = category,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
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
internal fun categoryBarColor(category: String): Color {
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
