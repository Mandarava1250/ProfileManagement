package com.example.personalassistant.feature.task.presentation.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 6b：详情页 BasicInfo section（只读视图）。
 *
 * 展示：标题 / 描述 / 优先级 / 截止日期 / 分类 / 完成状态。
 *
 * Sections 模式（plan v3.1 扩展点）：
 * - 此 section 只读 [task] 字段，不感知 subTasks / reminder / tags
 * - 未来加 ReminderSection / TagsSection 独立文件时，BasicInfo 不需要改动
 */
@Composable
fun BasicInfoSection(
    task: Task,
    onToggleComplete: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ===== 完成 + 标题 =====
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = onToggleComplete,
                )
                Text(
                    text = task.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
                PriorityDot(task.priority)
            }

            // ===== 描述 =====
            task.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ===== 元信息行（截止 / 分类 / 提醒）=====
            val metaRows = listOfNotNull(
                task.dueDate?.let { "截止：" to formatDueDate(it) },
                task.category?.takeIf { it.isNotBlank() }?.let { "分类：" to it },
                task.reminderTime?.let { "提醒：" to formatReminder(it) },
            )
            if (metaRows.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                metaRows.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityDot(priority: TaskPriority) {
    val color = when (priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.error
        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        TaskPriority.LOW -> MaterialTheme.colorScheme.outline
    }
    Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
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

private fun formatReminder(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    return "${date.monthValue}/${date.dayOfMonth} " +
        "${date.hour.toString().padStart(2, '0')}:" +
        "${date.minute.toString().padStart(2, '0')}"
}
