package com.example.personalassistant.feature.habit.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.core.ui.components.BigTitleHeader
import com.example.personalassistant.core.ui.components.SectionLabel
import com.example.personalassistant.core.ui.components.ThinDivider
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.habit.domain.util.HabitDates
import com.example.personalassistant.feature.habit.presentation.components.HabitEditorDialog
import com.example.personalassistant.feature.habit.presentation.components.HabitHeatmap
import com.example.personalassistant.feature.habit.presentation.components.HabitHeatmapLegend
import com.example.personalassistant.feature.habit.presentation.components.HabitMonthGrid
import com.example.personalassistant.feature.habit.presentation.components.HabitRow

/**
 * Phase 23：习惯页 → Phase 26 重画为「一物一块」。
 *
 * ## 这次改了什么
 *
 * 1. 顶栏：裸 `TopAppBar` → [BigTitleHeader]（26sp 大标题 + 12sp 副标题）。
 *    这是全 App 最后一个还在用裸 TopAppBar 的页面 —— M3 的 TopAppBar 高度固定 64dp、
 *    必须自带底色，跟这套「标题 + 副标题」的大标题区天然不合。
 * 2. 统计区降级：原来的「连续天数卡 + 月度表 + 年度热力图」三块占了半屏，
 *    现在收成**一行文字统计**，详细的月表 / 热力图收到底部链接「查看本月热力图」后面。
 * 3. 「今日」区改成一块一物：块与块之间用一条细分隔线（[ThinDivider]）分开，
 *    不再是「一张卡片 + 一个选中态底色」。
 */
@Composable
fun HabitScreen(
    onBack: () -> Unit,
    viewModel: HabitViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 「本周」要用到今天；它是墙上时钟不是状态，所以在这里取一次传进去
    val today = remember { HabitDates.todayStartMillis() }

    // 热力图默认收起：首屏只留一行统计 + 今日列表，详细的月表 / 热力图按需展开
    var statsExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // 失败提示 / 操作结果都走 Snackbar —— 校验不通过的原因必须让用户看见
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onDismissMessage()
    }

    Scaffold(
        topBar = {
            BigTitleHeader(
                title = "习惯",
                subtitle = state.subtitleText,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "更多",
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("在日历中显示习惯") },
                                onClick = {
                                    viewModel.onSetShowInCalendar(!state.showInCalendar)
                                    menuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("查看本月热力图") },
                                onClick = {
                                    statsExpanded = true
                                    menuExpanded = false
                                },
                            )
                        }
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
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                SectionLabel("今日", count = "${state.todayDoneCount} / ${state.todayTotalCount}")
            }

            if (state.todayItems.isEmpty()) {
                item { EmptyHint("还没有习惯，点右下角 + 新建一个") }
            }

            // 块与块之间一条细分隔线 —— 不画卡片壳，靠线分段。
            // 分隔线和习惯块放进同一个 item：itemsIndexed 的内容 lambda 里
            // 拿不到 LazyListScope 的 item()，没法再插一条分隔线。
            state.todayItems.forEachIndexed { index, habitItem ->
                item(key = "habit_${habitItem.habit.id}") {
                    if (index > 0) {
                        ThinDivider()
                    }
                    HabitRow(
                        item = habitItem,
                        onClick = { viewModel.onSelectHabit(habitItem.habit.id) },
                        onCheckIn = { viewModel.onCheckIn(habitItem.habit.id) },
                        onUndo = { viewModel.onUndoCheckIn(habitItem.habit.id) },
                    )
                }
            }

            // ===== 统计区：压成一行文字 =====
            state.stats?.let { stats ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stats.habit.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = " · 本周达成 ${(state.weekCompletionRate(today) * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ===== 底部链接：展开月表 + 热力图 =====
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 4.dp),
                ) {
                    TextButton(
                        onClick = { statsExpanded = !statsExpanded },
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    ) {
                        Text(
                            text = if (statsExpanded) "收起本月热力图" else "查看本月热力图",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (statsExpanded) {
                state.stats?.let { stats ->
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
                        SectionLabel("年度热力图")
                    }
                    item { HabitHeatmap(weeks = stats.heatmapWeeks) }
                    item {
                        Spacer(Modifier.height(AppSpacing.xs))
                        HabitHeatmapLegend()
                    }
                }
            }

            item {
                Spacer(Modifier.height(AppSpacing.xs))
                ThinDivider()
                Spacer(Modifier.height(AppSpacing.xs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = AppSpacing.xs),
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
                state.archivedHabits.forEachIndexed { index, habit ->
                    item(key = "archived_${habit.id}") {
                        if (index > 0) {
                            ThinDivider()
                        }
                        ArchivedRow(
                            name = habit.name,
                            onRestore = { viewModel.onToggleArchive(habit) },
                            onDelete = { viewModel.onDelete(habit) },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(AppSpacing.xs))
                ThinDivider()
                Spacer(Modifier.height(AppSpacing.xs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = AppSpacing.xs),
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

/** 空态提示 */
@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = AppSpacing.lg),
    )
}

/** 月份切换条 */
@Composable
private fun MonthSwitcher(
    title: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
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
            .padding(horizontal = 20.dp, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestore) { Text("恢复") }
        TextButton(onClick = onDelete) { Text("删除") }
    }
}
