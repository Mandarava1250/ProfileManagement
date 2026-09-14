package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskViewMode

/**
 * 优先级 TabRow（Phase 8b'）
 *
 * 6 个 Tab：「全部」+ P1/P2/P3/P4/未排期
 *
 * - 选中 Tab：粗体 + textColor = primary + Material 3 TabRow 默认下划线
 * - 每个 Tab 显示：别名 + 任务计数（"立即执行 (3)"）
 * - 长按 Tab 触发重命名（"全部" Tab 不可长按）
 *
 * 实现注意：Material 3 `Tab` 的 onClick 与 onLongClick 不冲突；
 * 这里用 [pointerInput] + [detectTapGestures] 在 Tab 外层包一层 Box 来捕获长按。
 */
@Composable
fun PriorityTabRow(
    displayPreferences: TaskDisplayPreferences,
    activeQuadrantCount: Map<TaskQuadrant, Int>,
    onSelectAll: () -> Unit,
    onSelectQuadrant: (TaskQuadrant) -> Unit,
    onLongPressQuadrant: (TaskQuadrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewMode = displayPreferences.viewMode
    val selectedQuadrant = displayPreferences.selectedQuadrant
    val isAllSelected = viewMode == TaskViewMode.ALL
    val totalCount = activeQuadrantCount.values.sum()

    // BY_QUADRANT 模式下若 selectedQuadrant 为 null，兜底 P1
    val selectedIndex: Int = if (isAllSelected) {
        0
    } else {
        (selectedQuadrant ?: TaskQuadrant.P1).ordinal + 1
    }

    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // ===== Tab 0：全部（不可长按）=====
        Tab(
            selected = isAllSelected,
            onClick = onSelectAll,
            text = { TabLabel(alias = "全部", count = totalCount, isSelected = isAllSelected) },
        )

        // ===== Tab 1~5：5 个象限（可长按重命名）=====
        TaskQuadrant.entries.forEach { quadrant ->
            val isSelected = !isAllSelected && selectedQuadrant == quadrant
            val alias = TaskDisplayPreferences.aliasOf(displayPreferences, quadrant)
            val count = activeQuadrantCount[quadrant] ?: 0

            Box(
                modifier = Modifier
                    .pointerInput(quadrant) {
                        detectTapGestures(
                            onLongPress = { onLongPressQuadrant(quadrant) },
                        )
                    },
            ) {
                Tab(
                    selected = isSelected,
                    onClick = { onSelectQuadrant(quadrant) },
                    text = { TabLabel(alias = alias, count = count, isSelected = isSelected) },
                )
            }
        }
    }
}

/**
 * Tab 内部统一的「名字 + 计数」格式（抽出避免重复代码）
 */
@Composable
private fun TabLabel(alias: String, count: Int, isSelected: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = alias,
            style = if (isSelected) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}