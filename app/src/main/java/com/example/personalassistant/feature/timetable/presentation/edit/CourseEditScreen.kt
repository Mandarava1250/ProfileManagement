package com.example.personalassistant.feature.timetable.presentation.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.AppTopBar
import com.example.personalassistant.core.ui.theme.CoursePalette
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.timetable.domain.model.Period
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule

/**
 * 课程编辑页：新增或编辑**整门课**（Phase 28：一门课 = 课程本体 + N 个课时）
 *
 * - route 参数 timetable/edit/{courseId}；-1L 表示新增
 * - 保存成功或删除成功会自动 popBackStack
 * - **时间下沉到课时列表**：每门课有 N 个课时，每个课时可独立增 / 删 / 改
 *   - 折叠态：`● 周一 08:00-09:40 第1节 🗑`（左边一条 3dp 课程色竖条）
 *   - 展开态：星期 chip + 时段模式切换 + 起止输入 + 该行自己的错误
 * - 错误**就近显示**：名称的错误在名称下面，课时的错误在那一行里
 * - **时间冲突**另外弹确认框（「取消」/「仍然保存」）—— 见 [CourseTimeConflict]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    courseId: Long,
    onBack: () -> Unit,
    viewModel: CourseEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(courseId) { viewModel.initialize(courseId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()

    LaunchedEffect(saved, deleted) {
        if (saved || deleted) onBack()
    }

    val accent = CoursePalette[state.colorIndex.coerceIn(0, CoursePalette.lastIndex)]

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(if (state.isEdit) stringResource(R.string.course_edit_title) else stringResource(R.string.course_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除")
                        }
                    }
                    IconButton(onClick = { viewModel.save() }, enabled = state.isValid && !state.isSaving) {
                        Icon(Icons.Rounded.Done, contentDescription = "保存")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("课程名称*") },
                singleLine = true,
                isError = state.nameError != null,
                modifier = Modifier.fillMaxWidth(),
            )

            // 名称的错误就近显示（不在页面底部堆）
            state.nameError?.let {
                Spacer(Modifier.height(4.dp))
                InlineError(it)
            }

            Spacer(Modifier.height(12.dp))

            // ====== 上课时间（= 课时列表）======
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "上课时间",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::addSession) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加课时")
                }
            }

            Spacer(Modifier.height(4.dp))

            // Phase 28 P9：按 **localKey** 定位，不按下标 —— 下标会随删除而错位
            state.sessions.forEach { session ->
                val key = session.localKey
                SessionRow(
                    session = session,
                    schedule = state.availableTimeSlots,
                    accent = accent,
                    canDelete = state.sessions.size > 1,
                    // Phase 28 P10：课程级的教师 / 地点 —— 课时没单独设置时回落用
                    courseTeacher = state.teacher,
                    courseLocation = state.location,
                    onToggle = { viewModel.toggleSessionExpanded(key) },
                    onDelete = { viewModel.removeSession(key) },
                    onDayChange = { viewModel.updateSessionDay(key, it) },
                    onModeChange = { viewModel.updateSessionMode(key, it) },
                    onStartChange = { viewModel.updateSessionStartMinutes(key, it) },
                    onEndChange = { viewModel.updateSessionEndMinutes(key, it) },
                    onStartPeriod = { viewModel.updateSessionStartPeriod(key, it) },
                    onEndPeriod = { viewModel.updateSessionEndPeriod(key, it) },
                    onTeacherChange = { viewModel.updateSessionTeacher(key, it) },
                    onLocationChange = { viewModel.updateSessionLocation(key, it) },
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(12.dp))

            Text("颜色", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            ColorPaletteRow(
                selected = state.colorIndex,
                onSelect = viewModel::updateColorIndex,
            )

            Spacer(Modifier.height(12.dp))
            Text("学期", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (state.availableSemesters.isEmpty() && !state.isEdit) {
                Text(
                    text = "还没有学期，请先到课表页创建一个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.availableSemesters) { name ->
                        val active = state.semester == name
                        FilterChip(
                            selected = active,
                            onClick = { viewModel.updateSemester(name) },
                            label = { Text(name) },
                            leadingIcon = if (active) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.weeks,
                onValueChange = viewModel::updateWeeks,
                label = { Text("周次（如 1-16，留空=全学期）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text("课前提醒（${state.reminderMinutes} 分钟）", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = state.reminderMinutes.toFloat(),
                onValueChange = { viewModel.updateReminderMinutes(it.toInt()) },
                valueRange = 0f..60f,
                steps = 11,
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // ====== 成绩（Phase 12 新增）======
            Text("成绩（留空 = 不计入 GPA）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.creditText,
                    onValueChange = viewModel::updateCreditText,
                    label = { Text("学分") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.gradePointText,
                    onValueChange = viewModel::updateGradePointText,
                    label = { Text("绩点 0.0-4.0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            state.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { viewModel.save() },
                enabled = state.isValid && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isEdit) "保存修改" else "添加课程")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ====== 时间冲突：「仍然保存」确认框 ======
    //
    // 只有**时间冲突**才弹这个框；普通校验错误（名称为空、课时时间非法）
    // 仍然是就近显示的行内错误，不打扰用户。
    state.conflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConflict() },
            title = { Text("时间冲突") },
            text = {
                Column {
                    Text(
                        text = conflict.summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "仍然保存会把冲突的课程一起写进课表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.save(force = true) }) {
                    Text("仍然保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConflict() }) {
                    Text("取消")
                }
            },
        )
    }
}

/**
 * 一个课时的行
 *
 * - 折叠态：一行摘要（点一下展开）—— 含时间 + 教师 / 地点
 * - 展开态：星期 chip + 时段模式切换 + 起止输入 + **教师 / 地点** + 这一行自己的错误
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(
    session: SessionEditState,
    schedule: TimeSlotSchedule,
    accent: Color,
    canDelete: Boolean,
    courseTeacher: String,
    courseLocation: String,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onDayChange: (Int) -> Unit,
    onModeChange: (TimeMode) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    onStartPeriod: (Int) -> Unit,
    onEndPeriod: (Int) -> Unit,
    onTeacherChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
) {
    val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val dayLabel = dayLabels.getOrElse(session.dayOfWeek - 1) { "周一" }
    // 折叠摘要里的「第 N 节」：开始时刻命中某一节时才显示
    val periodLabel = schedule.periods.firstOrNull { it.startMinutes == session.startMinutes }
        ?.let { "第${it.index}节" }
    // Phase 28 P10：摘要里显示**生效**的教师 / 地点（课时的值优先，空的回落课程级）
    val metaText = listOf(
        session.resolvedTeacher(courseTeacher),
        session.resolvedLocation(courseLocation),
    ).filter { it.isNotBlank() }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左边 3dp 的课程色竖条
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            // 圆点 + 摘要
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = buildString {
                    append(dayLabel)
                    append(' ')
                    append(DateUtils.formatMinutesOfDay(session.startMinutes))
                    append('-')
                    append(DateUtils.formatMinutesOfDay(session.endMinutes))
                    if (periodLabel != null) {
                        append(' ')
                        append(periodLabel)
                    }
                    // Phase 28 P10：教师 / 地点跟在时间后面（两个空格分隔）
                    if (metaText.isNotBlank()) {
                        append("  ")
                        append(metaText)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onToggle() },
            )
            IconButton(onClick = onToggle) {
                Text(
                    text = if (session.expanded) "收起" else "编辑",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "删除这个课时")
            }
        }

        if (session.expanded) {
            Spacer(Modifier.height(8.dp))

            // ====== 星期 ======
            Text(
                text = "星期",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(dayLabels) { idx, label ->
                    val active = session.dayOfWeek == idx + 1
                    FilterChip(
                        selected = active,
                        onClick = { onDayChange(idx + 1) },
                        label = { Text(label) },
                        leadingIcon = if (active) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ====== 时段模式 ======
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = session.mode == TimeMode.CLOCK,
                    onClick = { onModeChange(TimeMode.CLOCK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("时段（HH:mm）") }
                SegmentedButton(
                    selected = session.mode == TimeMode.PERIOD,
                    onClick = { onModeChange(TimeMode.PERIOD) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("选第 N 节") }
            }

            Spacer(Modifier.height(8.dp))

            when (session.mode) {
                TimeMode.CLOCK -> {
                    TimeRow(
                        label = "开始",
                        minutes = session.startMinutes,
                        onMinutesChange = onStartChange,
                    )
                    TimeRow(
                        label = "结束",
                        minutes = session.endMinutes,
                        onMinutesChange = onEndChange,
                    )
                }
                TimeMode.PERIOD -> PeriodPicker(
                    periods = schedule.periods,
                    startIndex = session.startPeriodIndex,
                    endIndex = session.endPeriodIndex,
                    onStartChange = onStartPeriod,
                    onEndChange = onEndPeriod,
                )
            }

            // ====== 教师 / 地点（Phase 28 P10：每个课时可单独设置）======
            // 留空 = 沿用课程级的（课程级那两栏在页面下方）
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = session.teacher,
                onValueChange = onTeacherChange,
                label = { Text("授课教师") },
                placeholder = {
                    Text(if (courseTeacher.isBlank()) "留空 = 不单独指定" else courseTeacher)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = session.location,
                onValueChange = onLocationChange,
                label = { Text("上课地点") },
                placeholder = {
                    Text(if (courseLocation.isBlank()) "留空 = 不单独指定" else courseLocation)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // 这一行自己的错误（冲突 / 时间非法）—— 就近显示在行内
            session.errorMessage?.let {
                Spacer(Modifier.height(6.dp))
                InlineError(it)
            }
        } else {
            // 折叠时也把错误露出来（不然用户看不到这一行出了问题）
            session.errorMessage?.let {
                Spacer(Modifier.height(4.dp))
                InlineError(it)
            }
        }
    }
}

/** 行内错误提示 */
@Composable
private fun InlineError(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun TimeRow(label: String, minutes: Int, onMinutesChange: (Int) -> Unit) {
    var text by remember(minutes) { mutableStateOf(DateUtils.formatMinutesOfDay(minutes)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(64.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { newText ->
                text = newText
                DateUtils.parseMinutesOfDay(newText)?.let(onMinutesChange)
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 选第 N 节：两个 chip 行（开始节、结束节）。
 *
 * 视觉上明显：每个 chip 标"第 N 节 HH:mm-HH:mm"，用 [FilterChip] 让"已选"实心 + check 图标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodPicker(
    periods: List<Period>,
    startIndex: Int,
    endIndex: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    if (periods.isEmpty()) {
        Text(
            text = "暂无课时表，请先到设置页配置",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    Column {
        PeriodRow(
            label = "开始节",
            periods = periods,
            selected = startIndex,
            onSelect = onStartChange,
        )
        Spacer(Modifier.height(4.dp))
        PeriodRow(
            label = "结束节",
            periods = periods,
            selected = endIndex,
            onSelect = onEndChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodRow(
    label: String,
    periods: List<Period>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(periods, key = { it.index }) { period ->
                val active = period.index == selected
                FilterChip(
                    selected = active,
                    onClick = { onSelect(period.index) },
                    label = {
                        Text(
                            "第${period.index}节  ${DateUtils.formatMinutesOfDay(period.startMinutes)}-${DateUtils.formatMinutesOfDay(period.endMinutes)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = if (active) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ColorPaletteRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoursePalette.forEachIndexed { index, color ->
            val isSelected = selected == index
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(index) }
            )
        }
    }
}
