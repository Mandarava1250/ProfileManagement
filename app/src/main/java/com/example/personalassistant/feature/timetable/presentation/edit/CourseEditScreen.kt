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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.core.ui.theme.CoursePalette
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.timetable.presentation.edit.CourseEditUiState
import com.example.personalassistant.feature.timetable.presentation.edit.CourseEditViewModel
import com.example.personalassistant.feature.timetable.presentation.edit.TimeMode

/**
 * 课程编辑页：新增或编辑课程
 *
 *  - route 参数 timetable/edit/{courseId}；-1L 表示新增
 *  - 保存成功或删除成功会自动 popBackStack
 *  - 时间支持两种模式：
 *    * 时段（默认）：HH:mm-HH:mm 自由输入
 *    * 课时：从 [CourseEditUiState.availableTimeSlots] 里选第 N 节
 *  - 星期用 [FilterChip] 显示选中态（实心填色 + Check 图标）
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "编辑课程" else "新增课程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                    IconButton(onClick = { viewModel.save() }, enabled = state.isValid && !state.isSaving) {
                        Icon(Icons.Filled.Done, contentDescription = "保存")
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
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // ====== 地点 + 教师（紧贴课程名下方）======
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::updateLocation,
                label = { Text("上课地点") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.teacher,
                onValueChange = viewModel::updateTeacher,
                label = { Text("授课教师") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // ====== 时间（在最上面 = 最重要的信息）======
            Text("时间", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.mode == TimeMode.CLOCK,
                    onClick = { viewModel.setMode(TimeMode.CLOCK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("时段（HH:mm）") }
                SegmentedButton(
                    selected = state.mode == TimeMode.PERIOD,
                    onClick = { viewModel.setMode(TimeMode.PERIOD) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("选第 N 节") }
            }

            Spacer(Modifier.height(8.dp))

            when (state.mode) {
                TimeMode.CLOCK -> {
                    TimeRow(
                        label = "开始",
                        minutes = state.startMinutes,
                        onMinutesChange = viewModel::updateStartMinutes,
                    )
                    TimeRow(
                        label = "结束",
                        minutes = state.endMinutes,
                        onMinutesChange = viewModel::updateEndMinutes,
                    )
                }
                TimeMode.PERIOD -> PeriodPicker(
                    periods = state.availableTimeSlots.periods,
                    startIndex = state.startPeriodIndex,
                    endIndex = state.endPeriodIndex,
                    onStartChange = viewModel::updateStartPeriod,
                    onEndChange = viewModel::updateEndPeriod,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ====== 星期（LazyRow 横滑避免 7 个 chip 挤出屏幕；7 个原本是 Row 一行被截）======
            Text("星期（单选）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(dayLabels) { idx, label ->
                    val active = state.dayOfWeek == idx + 1
                    FilterChip(
                        selected = active,
                        onClick = { viewModel.updateDayOfWeek(idx + 1) },
                        label = { Text(label) },
                        leadingIcon = if (active) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
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
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
                Spacer(Modifier.height(12.dp))
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
 * 选第 N 节：两个下拉（开始节、结束节）。
 *
 * 视觉上明显：每个 chip 标"第 N 节 HH:mm-HH:mm"，用 [FilterChip] 让"已选"实心 + check 图标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodPicker(
    periods: List<com.example.personalassistant.feature.timetable.domain.model.Period>,
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
    periods: List<com.example.personalassistant.feature.timetable.domain.model.Period>,
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
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
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
