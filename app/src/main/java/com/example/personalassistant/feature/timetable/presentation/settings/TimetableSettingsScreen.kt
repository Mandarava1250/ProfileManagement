package com.example.personalassistant.feature.timetable.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.timetable.domain.model.Period
import com.example.personalassistant.feature.timetable.domain.model.Semester
import com.example.personalassistant.feature.timetable.presentation.components.SemesterCreateDialog
import com.example.personalassistant.feature.timetable.presentation.components.SemesterEditDialog
import com.example.personalassistant.feature.timetable.presentation.settings.PeriodEditRow

/**
 * 课表设置页
 *
 * 顶部 Tab 切换：学期 / 课时表
 *  - 学期 Tab：列表 + 切当前 + 删除 + 新建（弹 [SemesterCreateDialog]）
 *  - 课时表 Tab：表格（每行 N / 开始 / 结束 + 删除） + 末尾"+ 添加一节" + 顶部"重置默认 45min" + 底部"保存"
 *
 * 学期编辑 = 直接操作 DB（[TimetableRepository.upsertSemester] / [switchSemester] / [deleteSemester]）
 * 课时表编辑 = 草稿模式（[TimetableSettingsViewModel.save] 显式落库）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableSettingsScreen(
    onBack: () -> Unit,
    viewModel: TimetableSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCreateSemester by remember { mutableStateOf(false) }
    var editingSemester by remember { mutableStateOf<Semester?>(null) }

    // Toast: VM 一旦 set toast，自动弹 Snackbar + 清空
    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("学期") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("课时表") },
                )
            }

            when (selectedTab) {
                0 -> SemesterTabContent(
                    state = state,
                    onAddSemester = { showCreateSemester = true },
                    onSetCurrent = viewModel::switchToSemester,
                    onDelete = viewModel::deleteSemester,
                    onEdit = { semester ->
                        editingSemester = semester
                    },
                )
                1 -> ScheduleTabContent(
                    state = state,
                    onAddRow = viewModel::addPeriodRow,
                    onRemoveRow = viewModel::removePeriodRow,
                    onUpdateRow = viewModel::updatePeriodRow,
                    onReset = viewModel::resetTimeSlots,
                    onSave = viewModel::save,
                )
            }
        }
    }

    if (showCreateSemester) {
        SemesterCreateDialog(
            onDismiss = { showCreateSemester = false },
            onConfirm = { name, start, end ->
                viewModel.addSemester(name, start, end)
                showCreateSemester = false
            },
        )
    }

    editingSemester?.let { target ->
        SemesterEditDialog(
            initialName = target.name,
            initialStart = target.startDate,
            initialEnd = target.endDate,
            onDismiss = { editingSemester = null },
            onConfirm = { oldName, newName, start, end ->
                viewModel.updateSemester(oldName, newName, start, end)
                editingSemester = null
            },
        )
    }
}

@Composable
private fun SemesterTabContent(
    state: TimetableSettingsUiState,
    onAddSemester: () -> Unit,
    onSetCurrent: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (Semester) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Semester?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("共 ${state.semesters.size} 个学期", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAddSemester) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("新建学期")
            }
        }

        if (state.semesters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无学期。点击右上角\"新建学期\"开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.semesters, key = { it.name }) { semester ->
                    SemesterRow(
                        semester = semester,
                        isCurrent = state.currentSemester?.name == semester.name,
                        onSetCurrent = { onSetCurrent(semester.name) },
                        onEdit = { onEdit(semester) },
                        onDelete = { pendingDelete = semester },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除学期") },
            text = { Text("确定删除\"${target.name}\"？已属于该学期的课程将变为无学期。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.name)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SemesterRow(
    semester: Semester,
    isCurrent: Boolean,
    onSetCurrent: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = semester.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = {}, label = { Text("当前") })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${DateUtils.formatDate(semester.startDate)} - ${DateUtils.formatDate(semester.endDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isCurrent) {
                TextButton(onClick = onSetCurrent) { Text("设为当前") }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑学期",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ScheduleTabContent(
    state: TimetableSettingsUiState,
    onAddRow: () -> Unit,
    onRemoveRow: (Int) -> Unit,
    onUpdateRow: (index: Int, startHour: Int?, startMinute: Int?, endHour: Int?, endMinute: Int?) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部说明 + 操作
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "共 ${state.editingSchedule.periods.size} 节课（45min/节）",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                TextButton(onClick = { showResetConfirm = true }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("重置默认")
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onSave) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
            }
        }

        // 表格：表头 + N 行
        ScheduleTable(
            periods = state.editingSchedule.periods,
            onRemove = onRemoveRow,
            onUpdate = onUpdateRow,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
        )

        // 底部添加
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onAddRow) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加一节")
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置为默认 12 节") },
            text = { Text("将清空自定义课时表，恢复为默认 8:00-21:20 的 12 节课（45min/节）。是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetConfirm = false
                }) { Text("重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ScheduleTable(
    periods: List<Period>,
    onRemove: (Int) -> Unit,
    onUpdate: (index: Int, startHour: Int?, startMinute: Int?, endHour: Int?, endMinute: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("节", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium)
            Text("开始", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text("结束", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(40.dp))  // 给删除按钮留位
        }
        HorizontalDivider()

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(periods, key = { it.index }) { period ->
                ScheduleRow(
                    period = period,
                    onRemove = { onRemove(period.index) },
                    onUpdate = { sh, sm, eh, em -> onUpdate(period.index, sh, sm, eh, em) },
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun ScheduleRow(
    period: Period,
    onRemove: () -> Unit,
    onUpdate: (startHour: Int?, startMinute: Int?, endHour: Int?, endMinute: Int?) -> Unit,
) {
    var sh by remember(period.index) { mutableIntStateOf(period.startMinutes / 60) }
    var sm by remember(period.index) { mutableIntStateOf(period.startMinutes % 60) }
    var eh by remember(period.index) { mutableIntStateOf(period.endMinutes / 60) }
    var em by remember(period.index) { mutableIntStateOf(period.endMinutes % 60) }

    // 字段变化 → 通知 VM 更新（VM 会做 index 重排 + 排序）
    fun pushUpdate() {
        onUpdate(sh, sm, eh, em)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${period.index}",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        // 开始时
        NumberField(
            value = sh,
            max = 23,
            onValueChange = { sh = it; pushUpdate() },
            modifier = Modifier.weight(0.5f),
        )
        Text(":", modifier = Modifier.padding(horizontal = 2.dp))
        NumberField(
            value = sm,
            max = 59,
            onValueChange = { sm = it; pushUpdate() },
            modifier = Modifier.weight(0.5f),
        )
        Spacer(Modifier.width(8.dp))
        // 结束时
        Text("-", modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.width(8.dp))
        NumberField(
            value = eh,
            max = 24,
            onValueChange = { eh = it; pushUpdate() },
            modifier = Modifier.weight(0.5f),
        )
        Text(":", modifier = Modifier.padding(horizontal = 2.dp))
        NumberField(
            value = em,
            max = 59,
            onValueChange = { em = it; pushUpdate() },
            modifier = Modifier.weight(0.5f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除这一节",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun NumberField(
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value.toString().padStart(2, '0'),
        onValueChange = { text ->
            val parsed = text.filter { it.isDigit() }.toIntOrNull() ?: 0
            onValueChange(parsed.coerceIn(0, max))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}
