package com.example.personalassistant.feature.task.presentation.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.presentation.components.ChecklistItem

/**
 * Phase 6b：详情页 Task section（可编辑子清单）。
 *
 * - 顶部：Section 标题 + 完成进度 badge（如「3/5」）
 * - 列表：复用现有 [ChecklistItem]
 * - 底部：输入框 + + 按钮，添加新子任务
 *
 * 复用 [ChecklistItem] 而不重新实现的好处：
 * - 与独立清单、编辑页子清单 UI 完全一致
 * - 任何对清单项样式的调整自动同步到三处
 */
@Composable
fun SubTaskSection(
    subTasks: List<Task>,
    completedCount: Int,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // ===== Section 标题 + 进度 badge =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "子清单",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$completedCount/${subTasks.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (subTasks.isEmpty()) {
                Text(
                    text = "还没有子任务，试试在下面加一条 ♪",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                subTasks.forEach { sub ->
                    ChecklistItem(
                        item = sub,
                        onToggleComplete = { onToggle(sub.id, it) },
                        onDelete = { onDelete(sub) },
                    )
                    HorizontalDivider()
                }
            }

            // ===== 添加输入 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    placeholder = { Text("添加子清单") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onAdd,
                    enabled = input.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "添加",
                        tint = if (input.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
