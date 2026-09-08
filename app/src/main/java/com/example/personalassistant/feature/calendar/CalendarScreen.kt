package com.example.personalassistant.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.CalendarViewType
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.presentation.CalendarViewModel
import com.example.personalassistant.feature.calendar.presentation.EventEditState
import com.example.personalassistant.feature.calendar.presentation.components.DayCalendarView
import com.example.personalassistant.feature.calendar.presentation.components.DayDetailSheet
import com.example.personalassistant.feature.calendar.presentation.components.EditEventSheet
import com.example.personalassistant.feature.calendar.presentation.components.MonthCalendarGrid
import com.example.personalassistant.feature.calendar.presentation.components.WeekCalendarView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 7c Sub-step 4+5：日历主页（三视图切换 + DayDetailSheet + EditEventSheet + FAB 新建）
 *
 * 涟宝解读：
 * - 顶部 TopAppBar：左/右按 viewType 切换单位（MONTH=月 / WEEK=周 / DAY=日） + 中间标题 + "回到今天"
 * - 中间：SingleChoiceSegmentedButtonRow（月/周/日切换）
 * - 主区：根据 viewType 渲染 MonthCalendarGrid / WeekCalendarView / DayCalendarView
 * - 底部：用户点击日期 → 弹出 DayDetailSheet（仅 MONTH/WEEK 视图触发）
 * - 浮动操作按钮（FAB）：点击 → 启动 EditEventSheet 新建事件（按 selectedDate/今天）
 * - DayDetailSheet 内事件项点击 → 启动 EditEventSheet 编辑
 *
 * @param navController 保留参数供 Phase 8 导航使用（暂时 unused）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavHostController,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val viewType by viewModel.viewType.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectedDayDetail by viewModel.selectedDayDetail.collectAsStateWithLifecycle()
    val weekCells by viewModel.weekCells.collectAsStateWithLifecycle()
    val editingState by viewModel.editingState.collectAsStateWithLifecycle()
    val editorError by viewModel.editorError.collectAsStateWithLifecycle()
    val eventCategories by viewModel.eventCategories.collectAsStateWithLifecycle()
    val showCategoryManager by viewModel.showCategoryManager.collectAsStateWithLifecycle()
    val categoryManagerError by viewModel.categoryManagerError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(editorError) {
        editorError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = headerTitle(viewType, uiState.yearMonth, selectedDate)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goToPrevious() }) {
                        Icon(prevIcon(viewType), contentDescription = "上一个")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToCurrent() }) {
                        Icon(Icons.Filled.Today, contentDescription = "回到今天")
                    }
                    IconButton(onClick = { viewModel.goToNext() }) {
                        Icon(nextIcon(viewType), contentDescription = "下一个")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startAddEvent() }) {
                Icon(Icons.Filled.Add, contentDescription = "新建事件")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 视图切换 SegmentedButton
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                CalendarViewType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = viewType == type,
                        onClick = { viewModel.setViewType(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = CalendarViewType.entries.size,
                        ),
                    ) {
                        Text(text = type.label)
                    }
                }
            }

            // 主视图
            Box(modifier = Modifier.fillMaxSize()) {
                when (viewType) {
                    CalendarViewType.MONTH -> MonthCalendarGrid(
                        cells = uiState.monthCells,
                        selectedDate = selectedDate,
                        onDateClick = viewModel::onDateClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CalendarViewType.WEEK -> WeekCalendarView(
                        cells = weekCells,
                        selectedDate = selectedDate,
                        onDateClick = viewModel::onDateClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CalendarViewType.DAY -> {
                        val anchor = selectedDate
                        if (anchor != null) {
                            // 复用 selectedDayDetail（已经是 selectedDate 派生）
                            DayCalendarView(
                                date = anchor,
                                dayDetail = selectedDayDetail,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            EmptyViewPlaceholder(text = "请回到今天")
                        }
                    }
                }
            }
        }

        // BottomSheet：MONTH/WEEK 视图点击日期时弹出
        if (selectedDate != null && viewType != CalendarViewType.DAY) {
            DayDetailSheet(
                date = selectedDate!!,
                dayDetail = selectedDayDetail,
                onDismiss = { viewModel.dismissDayDetail() },
                onEventClick = { event ->
                    viewModel.dismissDayDetail()
                    viewModel.startEditEvent(event)
                },
            )
        }

        // Sub-step 5：EditEventSheet（事件编辑器）
        editingState?.let { state ->
            EditEventSheet(
                state = state,
                availableCategories = eventCategories,
                onTitleChange = { newTitle ->
                    viewModel.updateEditingState(state.copy(title = newTitle))
                },
                onToggleAllDay = { newAllDay ->
                    viewModel.updateEditingState(state.copy(isAllDay = newAllDay))
                },
                onToggleHasTime = { newHasTime ->
                    viewModel.updateEditingState(
                        state.copy(
                            hasTime = newHasTime,
                            startHour = if (newHasTime && state.startHour == null) 9 else state.startHour,
                            startMinute = if (newHasTime && state.startMinute == null) 0 else state.startMinute,
                            endHour = if (newHasTime && state.endHour == null) 10 else state.endHour,
                            endMinute = if (newHasTime && state.endMinute == null) 0 else state.endMinute,
                        ),
                    )
                },
                onStartTimeChange = { h, m ->
                    viewModel.updateEditingState(state.copy(startHour = h, startMinute = m))
                },
                onEndTimeChange = { h, m ->
                    viewModel.updateEditingState(state.copy(endHour = h, endMinute = m))
                },
                onCategoryChange = { categoryId ->
                    // Phase 8 Sub-step 9：传入 Long? categoryId（不再是 String 名称）
                    viewModel.onEventCategoryChange(categoryId)
                },
                onManageCategories = viewModel::openCategoryManager,
                onNoteChange = { newNote ->
                    viewModel.updateEditingState(state.copy(note = newNote))
                },
                onReminderChange = { newReminder ->
                    viewModel.updateEditingState(state.copy(reminderMinutes = newReminder))
                },
                onSave = { viewModel.saveEvent(state) },
                onDelete = { viewModel.deleteEventFromEditor() },
                onDismiss = { viewModel.clearEditor() },
            )
        }

        // ===== Phase 8 Sub-step 9：事件分类管理 Dialog =====
        if (showCategoryManager) {
            com.example.personalassistant.feature.calendar.presentation.components.CategoryManagementDialog(
                categories = eventCategories,
                onDismiss = { viewModel.closeCategoryManager() },
                onUpsert = { viewModel.upsertCategory(it) },
                onDelete = { viewModel.deleteCategory(it) },
                errorMessage = categoryManagerError,
                onClearError = { viewModel.clearCategoryManagerError() },
            )
        }
    }
}

/** 顶部标题：MONTH=月份；WEEK=周首末日期；DAY=具体日期 */
private fun headerTitle(
    viewType: CalendarViewType,
    yearMonth: String,
    selectedDate: Long?,
): String = when (viewType) {
    CalendarViewType.MONTH -> {
        val parts = yearMonth.split("-")
        if (parts.size == 2) "${parts[0]} 年 ${parts[1].toInt()} 月" else yearMonth
    }
    CalendarViewType.WEEK -> {
        if (selectedDate == null) "本周"
        else weekRangeLabel(selectedDate)
    }
    CalendarViewType.DAY -> {
        if (selectedDate == null) "今日"
        else DateUtils.formatChineseDate(selectedDate)
    }
}

private fun weekRangeLabel(date: Long): String {
    val zone = ZoneId.systemDefault()
    val (weekStart, weekEndExclusive) = CalendarViewModel.computeWeekRange(date)
    val start = Instant.ofEpochMilli(weekStart).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(weekEndExclusive - 1).atZone(zone).toLocalDate()
    val sMonth = start.monthValue
    val eMonth = end.monthValue
    return if (sMonth == eMonth) {
        "${sMonth}月${start.dayOfMonth}日 - ${end.dayOfMonth}日"
    } else {
        "${sMonth}月${start.dayOfMonth}日 - ${eMonth}月${end.dayOfMonth}日"
    }
}

private fun prevIcon(viewType: CalendarViewType) = when (viewType) {
    CalendarViewType.MONTH -> Icons.Filled.ChevronLeft
    CalendarViewType.WEEK -> Icons.Filled.ChevronLeft
    CalendarViewType.DAY -> Icons.Filled.ChevronLeft
}

private fun nextIcon(viewType: CalendarViewType) = when (viewType) {
    CalendarViewType.MONTH -> Icons.Filled.ChevronRight
    CalendarViewType.WEEK -> Icons.Filled.ChevronRight
    CalendarViewType.DAY -> Icons.Filled.ChevronRight
}

@Composable
private fun EmptyViewPlaceholder(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** CalendarViewType 的中文标签，UI 用 */
private val CalendarViewType.label: String
    get() = when (this) {
        CalendarViewType.MONTH -> "月"
        CalendarViewType.WEEK -> "周"
        CalendarViewType.DAY -> "日"
    }
