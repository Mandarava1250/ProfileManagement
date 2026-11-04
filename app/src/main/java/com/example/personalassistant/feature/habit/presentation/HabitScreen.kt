package com.example.personalassistant.feature.habit.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.habit.domain.model.HabitStats
import com.example.personalassistant.feature.habit.presentation.components.HabitEditorDialog
import com.example.personalassistant.feature.habit.presentation.components.HabitHeatmap
import com.example.personalassistant.feature.habit.presentation.components.HabitHeatmapLegend
import com.example.personalassistant.feature.habit.presentation.components.HabitMonthGrid
import com.example.personalassistant.feature.habit.presentation.components.HabitRow

/**
 * Phase 23：习惯页
 *
 * 三段：
 * 1. **今日** —— 打卡列表（点 + 打卡一次）
 * 2. **统计** —— 选中习惯的连续天数 / 月度打卡表 / 年度热力图
 * 3. **已归档** —— 折叠区
 */
@Composable
fun HabitScreen(
    onBack: () -> Unit,
    viewModel: HabitViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 失败提示 / 操作结果都走 Snackbar —— 校验不通过的原因必须让用户看见
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onDismissMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("习惯") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onStartCreate() }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "新建习惯",
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            contentPadding = PaddingValues(top = AppSpacing.sm, bottom = AppSpacing.xl),
        ) {
            item { SectionTitle("今日") }

            if (state.todayItems.isEmpty()) {
                item { EmptyHint("还没有习惯，点右下角 + 新建一个") }
            }

            items(state.todayItems, key = { it.habit.id }) { item ->
                HabitRow(
                    item = item,
                    selected = item.habit.id == state.selectedHabitId,
                    onClick = { viewModel.onSelectHabit(item.habit.id) },
                    onCheckIn = { viewModel.onCheckIn(item.habit.id) },
                    onUndo = { viewModel.onUndoCheckIn(item.habit.id) },
                )
            }

            state.stats?.let { stats ->
                item {
                    Spacer(Modifier.height(AppSpacing.xs))
                    SectionTitle("统计 · ${stats.habit.name}")
                }
                item { StreakCard(stats) }
                item {
                    MonthSwitcher(
                        title = state.displayYearMonthText,
                        onPrev = { viewModel.onPrevMonth() },
                        onNext = { viewModel.onNextMonth() },
                    )
                }
                item { HabitMonthGrid(cells = stats.monthCells) }
                item {
                    Spacer(Modifier.height(AppSpacing.xs))
                    SectionTitle("年度热力图")
                }
                item { HabitHeatmap(weeks = stats.heatmapWeeks) }
                item { HabitHeatmapLegend() }
            }

            item {
                Spacer(Modifier.height(AppSpacing.xs))
                HorizontalDivider()
                Spacer(Modifier.height(AppSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已归档（${state.archivedHabits.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.showArchived,
                        onCheckedChange = { viewModel.onToggleShowArchived() },
                    )
                }
            }

            if (state.showArchived) {
                if (state.archivedHabits.isEmpty()) {
                    item { EmptyHint("没有归档的习惯") }
                }
                items(state.archivedHabits, key = { it.id }) { habit ->
                    ArchivedRow(
                        name = habit.name,
                        onRestore = { viewModel.onToggleArchive(habit) },
                        onDelete = { viewModel.onDelete(habit) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(AppSpacing.xs))
                HorizontalDivider()
                Spacer(Modifier.height(AppSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "在日历中显示习惯", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "在日历里显示习惯打卡",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.showInCalendar,
                        onCheckedChange = { viewModel.onSetShowInCalendar(it) },
                    )
                }
            }
        }
    }

    state.editor?.let { editorState ->
        HabitEditorDialog(
            state = editorState,
            onChange = viewModel::onEditorChange,
            onConfirm = viewModel::onSaveEditor,
            onDismiss = { viewModel.onDismissEditor() },
        )
    }
}

/** 区块标题 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = AppSpacing.xxs),
    )
}

/** 空态提示 */
@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = AppSpacing.lg),
    )
}

/** 连续天数卡片 */
@Composable
private fun StreakCard(stats: HabitStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Text(
                text = stats.streakText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stats.habit.targetDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (stats.todayMet) "今天已达成" else "今天还没达成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 月份切换条 */
@Composable
private fun MonthSwitcher(
    title: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = "上个月",
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "下个月",
            )
        }
    }
}

/** 归档区的一行 */
@Composable
private fun ArchivedRow(
    name: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = onRestore) { Text("恢复") }
        androidx.compose.material3.TextButton(onClick = onDelete) { Text("删除") }
    }
}
