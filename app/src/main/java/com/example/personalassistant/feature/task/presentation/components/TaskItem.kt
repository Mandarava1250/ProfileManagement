package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.components.AppCard
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import kotlin.math.roundToInt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 任务卡（主页四象限 + 已完成 共用）
 *
 * - 整卡点击 → onClick（打开编辑页）
 * - 勾选 → onToggleComplete
 * - 删除图标 → onDelete
 * - 右侧色点：按优先级着色
 *
 * Phase 14：外壳换成 [AppCard]（统一圆角 20dp），
 * 优先级色改走语义色 token，不再散落在 colorScheme 里凑。
 */
@Composable
fun TaskItem(
    task: Task,
    onToggleComplete: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    // ===== Phase 25：拖拽排序 =====
    /** 是否允许拖拽（只有「自定义顺序」排序下才为真） */
    dragEnabled: Boolean = false,
    /** 该项正被拖着（浮起来 + 跟手偏移） */
    isDragging: Boolean = false,
    /** 跟手位移（像素，向下为正） */
    dragOffsetY: Float = 0f,
    /** 认定为拖拽的那一刻 */
    onDragStart: () -> Unit = {},
    /** 拖拽过程中的 Y 位移增量（像素） */
    onDrag: (Float) -> Unit = {},
    /** 拖拽结束 */
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Phase 14：优先级色收进语义 token（高 / 中 / 低 三档 + 中性）
    // - URGENT / HIGH → 高
    // - MEDIUM → 中
    // - NORMAL → 低
    // - LOW → 中性灰
    val appColors = LocalAppColors.current
    val priorityColor = when (task.priority) {
        TaskPriority.URGENT -> appColors.priorityHighInk
        TaskPriority.HIGH -> appColors.priorityHighInk
        TaskPriority.MEDIUM -> appColors.priorityMediumInk
        TaskPriority.NORMAL -> appColors.priorityLowInk
        TaskPriority.LOW -> MaterialTheme.colorScheme.outline
    }

    // Phase 25：手势仲裁层（长按竖拖 = 排序）
    // 任务页没有多选模式，所以 enableLongPress = false —— 只留拖拽这一条路。
    ChecklistItemGestureBox(
        enableLongPress = false,
        enableDrag = dragEnabled,
        onDragStart = onDragStart,
        onDrag = onDrag,
        onDragEnd = onDragEnd,
    ) {
        AppCard(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                // Phase 25：被拖起来的那项浮起来一点，跟手时能看清是哪一条
                .shadow(if (isDragging) 6.dp else 0.dp)
                // Phase 25：跟手位移（被拖着的那一项跟着手指走）
                .offset { IntOffset(0, dragOffsetY.roundToInt()) },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentPadding = PaddingValues(AppSpacing.xs),
            // 列表里密排的卡片不要阴影，否则一屏几十个会显得脏
            elevated = false,
            onClick = onClick,
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = onToggleComplete,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
                task.dueDate?.let { due ->
                    Text(
                        text = formatDueDate(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Phase 14：优先级不只靠色点 —— 单靠颜色分不出高/中/低，
            // 补一个文字标签（与编辑页共用同一套 TaskPriority.label）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(priorityColor),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = task.priority.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = priorityColor,
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
    }
}

private fun formatDueDate(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.minusDays(1) -> "昨天"
        else -> if (date.year == today.year) {
            "${date.monthValue}/${date.dayOfMonth}"
        } else {
            "${date.year}/${date.monthValue}/${date.dayOfMonth}"
        }
    }
}
