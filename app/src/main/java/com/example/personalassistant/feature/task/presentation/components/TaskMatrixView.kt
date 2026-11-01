package com.example.personalassistant.feature.task.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.task.domain.model.MatrixCell
import com.example.personalassistant.feature.task.domain.model.MatrixRegion
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskMatrix
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant

/**
 * 任务矩阵视图（Phase 8final G2）：**矩阵视图 / 2×2 分组**。
 *
 * 术语：不要把行/列标注成「重要 / 不重要」这类二元硬轴。
 * 每个格子的内容与 5 Tab 列表视图的对应分组**完全一致**
 * （见 [TaskMatrix.from]，本组件不做任何重新分类）。
 *
 * ## 两种布局（断点 [TaskMatrix.MATRIX_BREAKPOINT_DP]）
 *
 * - 宽度 >= 600dp：[MatrixGridLayout] —— 未排期区在顶部，下方 2×2 四格，
 *   每格内独立滚动
 * - 宽度 < 600dp：[MatrixStackedLayout] —— **5 个区域纵向铺开**，
 *   顺序固定为 [MatrixRegion.VERTICAL_ORDER]（未排期区在最顶部）
 *
 * ## 为什么不嵌套滚动容器
 *
 * Compose 里「外层可垂直滚动 + 内层再垂直滚动」同方向嵌套会滚动异常。
 * 因此：
 * - 2×2 布局：外层是**不滚动**的 Column（用 weight 均分高度），
 *   滚动只发生在每个格子内部的 [LazyColumn]
 * - 纵向布局：外层就是唯一的 [LazyColumn]，区域内任务直接铺开，不再套滚动容器
 *
 * ## UNSCHEDULED 区域
 *
 * 独立区域，不计入四格总数（[TaskMatrix.cellTotal]）；**无拖拽、无移入移出**，
 * 只能通过编辑任务的 dueDate / priority 改变归属。
 */
@Composable
fun TaskMatrixView(
    matrix: TaskMatrix,
    displayPreferences: TaskDisplayPreferences,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClickTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    if (TaskMatrix.isVerticalLayout(screenWidthDp)) {
        MatrixStackedLayout(
            matrix = matrix,
            displayPreferences = displayPreferences,
            onToggleComplete = onToggleComplete,
            onClickTask = onClickTask,
            onDelete = onDelete,
            modifier = modifier,
        )
    } else {
        MatrixGridLayout(
            matrix = matrix,
            displayPreferences = displayPreferences,
            onToggleComplete = onToggleComplete,
            onClickTask = onClickTask,
            onDelete = onDelete,
            modifier = modifier,
        )
    }
}

// ==================== 布局 A：2×2 网格（>= 600dp） ====================

@Composable
private fun MatrixGridLayout(
    matrix: TaskMatrix,
    displayPreferences: TaskDisplayPreferences,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClickTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 格子 → 任务列表：矩阵没变时映射也不会变，预先算好一次再缓存，
    // 别在 forEach 里反复去 matrix 里取
    val tasksByCell: Map<MatrixCell, List<Task>> = remember(matrix) {
        MatrixCell.CELLS.associateWith { matrix.tasksOf(it) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 未排期区：独立于 2×2，放在矩阵上方（与纵向布局顺序保持一致）
        if (matrix.hasUnscheduled) {
            UnscheduledSection(
                tasks = matrix.unscheduled,
                displayName = aliasOf(displayPreferences, TaskQuadrant.UNSCHEDULED),
                onToggleComplete = onToggleComplete,
                onClickTask = onClickTask,
                onDelete = onDelete,
            )
        }

        MatrixCell.ROWS.forEach { rowCells ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                rowCells.forEach { cell ->
                    MatrixCellCard(
                        cell = cell,
                        tasks = tasksByCell[cell].orEmpty(),
                        displayName = aliasOf(displayPreferences, cell.quadrant),
                        onToggleComplete = onToggleComplete,
                        onClickTask = onClickTask,
                        onDelete = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixCellCard(
    cell: MatrixCell,
    tasks: List<Task>,
    displayName: String,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClickTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RegionHeader(
                quadrant = cell.quadrant,
                displayName = displayName,
                count = tasks.size,
            )
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "无任务 ♪",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 外层 Column 不滚动，滚动只发生在这一层，避免同方向嵌套滚动
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(tasks, key = { "cell${cell.position}-${it.id}" }) { task ->
                        MatrixTaskRow(
                            task = task,
                            onToggleComplete = onToggleComplete,
                            onClickTask = onClickTask,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
    }
}

// ==================== 布局 B：纵向 5 区（< 600dp） ====================

@Composable
private fun MatrixStackedLayout(
    matrix: TaskMatrix,
    displayPreferences: TaskDisplayPreferences,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClickTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 区域 → 任务列表：矩阵没变时映射也不会变，预先算好一次再缓存
    val tasksByRegion: Map<MatrixRegion, List<Task>> = remember(matrix) {
        MatrixRegion.VERTICAL_ORDER.associateWith { matrix.tasksOf(it) }
    }

    // 单一滚动容器：区域内任务直接铺开，不再嵌套 LazyColumn
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        MatrixRegion.VERTICAL_ORDER.forEach { region ->
            val tasks = tasksByRegion[region].orEmpty()
            // 未排期区为空时整块省略；4 个格子即使为空也保留占位（避免布局跳动）
            if (region.isInMatrix || tasks.isNotEmpty()) {
                item(key = "region-header-${region.name}") {
                    RegionHeader(
                        quadrant = region.quadrant,
                        displayName = aliasOf(displayPreferences, region.quadrant),
                        count = tasks.size,
                    )
                }
                if (tasks.isEmpty()) {
                    item(key = "region-empty-${region.name}") {
                        Text(
                            text = "无任务 ♪",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    items(tasks, key = { "region${region.name}-${it.id}" }) { task ->
                        MatrixTaskRow(
                            task = task,
                            onToggleComplete = onToggleComplete,
                            onClickTask = onClickTask,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
    }
}

// ==================== 共用零件 ====================

/**
 * 未排期区（独立区域）。
 *
 * 任务多时默认只显示前 [TaskMatrix.UNSCHEDULED_PREVIEW_COUNT] 条，
 * 其余折叠，由「查看全部 N 条」展开——避免顶部区域把 4 格挤出屏幕。
 * 2×2 与纵向两种布局共用这一套折叠规则，行为一致可预期。
 */
@Composable
private fun UnscheduledSection(
    tasks: List<Task>,
    displayName: String,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClickTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val previewCount = TaskMatrix.UNSCHEDULED_PREVIEW_COUNT
    val overflow = tasks.size - previewCount
    val visible = if (expanded || overflow <= 0) tasks else tasks.take(previewCount)

    Column(modifier = Modifier.fillMaxWidth()) {
        RegionHeader(
            quadrant = TaskQuadrant.UNSCHEDULED,
            displayName = displayName,
            count = tasks.size,
        )
        visible.forEach { task ->
            MatrixTaskRow(
                task = task,
                onToggleComplete = onToggleComplete,
                onClickTask = onClickTask,
                onDelete = onDelete,
            )
        }
        if (overflow > 0) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(text = if (expanded) "收起" else "查看全部 $overflow 条")
            }
        }
    }
}

/**
 * 区域头：色点 + 别名 + 计数。
 *
 * 与 [QuadrantSection] 同构，但去掉了 fillMaxWidth 之外的大内边距，
 * 让它在 2×2 的小格子里也能塞得下。色点复用同一套 quadrant→color 映射，
 * 保证矩阵与列表视图对同一个分组显示同一个颜色。
 */
@Composable
private fun RegionHeader(
    quadrant: TaskQuadrant,
    displayName: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(quadrantColor(quadrant)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelLarge,
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

@Composable
private fun MatrixTaskRow(
    task: Task,
    onToggleComplete: (Long, Boolean) -> Unit,
    onClickTask: (Long) -> Unit,
    onDelete: (Task) -> Unit,
) {
    TaskItem(
        task = task,
        onToggleComplete = { onToggleComplete(task.id, it) },
        onClick = { onClickTask(task.id) },
        onDelete = { onDelete(task) },
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** quadrant → 颜色，与 [QuadrantSection] 保持一致 */
@Composable
private fun quadrantColor(quadrant: TaskQuadrant) = when (quadrant) {
    TaskQuadrant.P1 -> MaterialTheme.colorScheme.error
    TaskQuadrant.P2 -> MaterialTheme.colorScheme.tertiary
    TaskQuadrant.P3 -> MaterialTheme.colorScheme.primary
    TaskQuadrant.P4 -> MaterialTheme.colorScheme.outline
    TaskQuadrant.UNSCHEDULED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 取分组别名（用户自定义名，未配置时回退 enum.label） */
private fun aliasOf(prefs: TaskDisplayPreferences, quadrant: TaskQuadrant): String =
    TaskDisplayPreferences.aliasOf(prefs, quadrant)

/**
 * 视图模式切换器：「列表」/「矩阵」两档。
 *
 * 放在 [PriorityTabRow] 下方。语义：
 * - 矩阵与「全部」显示同一份数据（见 [TaskMatrix.from]），因此矩阵模式下
 *   TabRow 仍高亮「全部」，切回「列表」时回到 [com.example.personalassistant.feature.task.domain.model.TaskViewMode.ALL]
 * - 点具体分组 Tab 会切到 BY_QUADRANT，等效退出矩阵，符合直觉
 */
@Composable
fun MatrixViewToggle(
    isMatrixMode: Boolean,
    onSelectList: () -> Unit,
    onSelectMatrix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "视图",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleChip(label = "列表", selected = !isMatrixMode, onClick = onSelectList)
        ToggleChip(label = "矩阵", selected = isMatrixMode, onClick = onSelectMatrix)
    }
}

@Composable
private fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
