package com.example.personalassistant.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.BigTitleHeader
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.AgendaScope
import com.example.personalassistant.feature.calendar.presentation.CalendarScreenMode
import com.example.personalassistant.feature.calendar.presentation.CalendarViewModel
import com.example.personalassistant.feature.calendar.presentation.components.AgendaKind
import com.example.personalassistant.feature.calendar.presentation.components.AgendaStream
import com.example.personalassistant.feature.calendar.presentation.components.CalendarDisplaySettingsSheet
import com.example.personalassistant.feature.calendar.presentation.components.DayCalendarView
import com.example.personalassistant.feature.calendar.presentation.components.EditEventSheet
import com.example.personalassistant.feature.calendar.presentation.components.HabitCheckInSheet
import com.example.personalassistant.feature.calendar.presentation.components.MonthCalendarGrid
import com.example.personalassistant.feature.calendar.presentation.components.MonthPickerSheet
import com.example.personalassistant.feature.calendar.presentation.components.MonthTagList
import com.example.personalassistant.feature.calendar.presentation.components.TaskTimeRangeSheet
import com.example.personalassistant.feature.calendar.presentation.components.WeekCalendarView
import com.example.personalassistant.navigation.Screen
import com.example.personalassistant.navigation.navigateToTab
import kotlinx.coroutines.launch

/**
 * 日历页（Phase 26）。
 *
 * ## 视图 = 路由
 *
 * 六种「看的方式」各有一条独立路由：
 * 日程 / 工作日日程 / 休息日日程 / 月 / 周 / 日。
 * 导航栏右侧第二个（从右往左数）是**视图切换器**，点开就能在这些视图之间跳。
 *
 * 之前这些视图是页内标签页 + 本地 state，切换状态会丢；拆成路由之后
 * 系统返回键、进程重建、深链都能正常工作。
 *
 * @param mode 这一屏显示哪种视图，由路由决定
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavHostController,
    mode: CalendarScreenMode = CalendarScreenMode.AGENDA_DAY,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val selectedDayDetail by viewModel.selectedDayDetail.collectAsStateWithLifecycle()
    val agendaDays by viewModel.agendaDays.collectAsStateWithLifecycle()
    val editingState by viewModel.editingState.collectAsStateWithLifecycle()
    val editorError by viewModel.editorError.collectAsStateWithLifecycle()
    val eventCategories by viewModel.eventCategories.collectAsStateWithLifecycle()
    val showCategoryManager by viewModel.showCategoryManager.collectAsStateWithLifecycle()
    val categoryManagerError by viewModel.categoryManagerError.collectAsStateWithLifecycle()
    val todayOverview by viewModel.todayOverview.collectAsStateWithLifecycle()
    val weekTimeline by viewModel.weekTimeline.collectAsStateWithLifecycle()
    val displayPreferences by viewModel.displayPreferences.collectAsStateWithLifecycle()
    val dayConflicts by viewModel.dayConflicts.collectAsStateWithLifecycle()
    val editingTaskTimeRange by viewModel.editingTaskTimeRange.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 路由变了就把 VM 切到对应视图（聚合范围 + 翻页单位）
    LaunchedEffect(mode) { viewModel.applyMode(mode) }

    LaunchedEffect(editorError) {
        editorError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    var showMonthPicker by remember { mutableStateOf(false) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showHabitCheckInSheet by remember { mutableStateOf(false) }

    if (showMonthPicker) {
        MonthPickerSheet(
            currentYearMonth = uiState.yearMonth,
            onPick = { year, month ->
                viewModel.goToYearMonth(year, month)
                showMonthPicker = false
            },
            onDismiss = { showMonthPicker = false },
        )
    }

    if (showDisplaySettings) {
        CalendarDisplaySettingsSheet(
            preferences = displayPreferences,
            onShowCoursesChange = viewModel::setShowCourses,
            onShowTasksChange = viewModel::setShowTasks,
            onShowCompletedChange = viewModel::setShowCompleted,
            onShowHabitsChange = viewModel::setShowHabits,
            onDismiss = { showDisplaySettings = false },
        )
    }

    editingTaskTimeRange?.let { task ->
        TaskTimeRangeSheet(
            task = task,
            defaultDayStart = selectedDate ?: DateUtils.todayStartMillis(),
            onSave = { startAt, endAt -> viewModel.saveTaskTimeRange(task.id, startAt, endAt) },
            onClear = { viewModel.saveTaskTimeRange(task.id, null, null) },
            onDismiss = { viewModel.clearTaskTimeRangeEditor() },
        )
    }

    if (showHabitCheckInSheet) {
        val targetDate = selectedDate ?: DateUtils.todayStartMillis()
        HabitCheckInSheet(
            date = targetDate,
            habits = selectedDayDetail?.habits.orEmpty(),
            onCheckIn = { habitId, delta -> viewModel.checkInHabit(habitId, targetDate, delta) },
            onDismiss = { showHabitCheckInSheet = false },
        )
    }

    val itemCount = agendaDays.sumOf { it.items.size }
    val subtitle = when (mode) {
        CalendarScreenMode.AGENDA_DAY -> "今天 $itemCount 项安排 ⌄"
        CalendarScreenMode.AGENDA_WORKDAY -> "本周工作日 $itemCount 项安排 ⌄"
        CalendarScreenMode.AGENDA_RESTDAY -> "本周休息日 $itemCount 项安排 ⌄"
        CalendarScreenMode.MONTH -> "本月安排 ⌄"
        CalendarScreenMode.WEEK -> "本周安排 ⌄"
        CalendarScreenMode.DAY -> "当日安排 ⌄"
    }

    Scaffold(
        topBar = {
            BigTitleHeader(
                title = monthTitle(uiState.yearMonth),
                subtitle = subtitle,
                navigationIcon = {
                    // 左侧：时间导航（上一个 / 回到今天）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.goToPrevious() }) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一个")
                        }
                        IconButton(onClick = { viewModel.goToCurrent() }) {
                            Icon(Icons.Rounded.Today, contentDescription = "回到今天")
                        }
                    }
                },
                actions = {
                    // 右侧从左到右：显示设置 / 选择月份 / 视图切换器 / 下一个
                    // 「视图切换器」落在倒数第二位 —— 从右往左数第二个就是它。
                    IconButton(onClick = { showDisplaySettings = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "显示设置")
                    }
                    if (mode == CalendarScreenMode.MONTH) {
                        IconButton(onClick = { showMonthPicker = true }) {
                            Icon(Icons.Rounded.DateRange, contentDescription = "选择月份")
                        }
                    }
                    CalendarViewSwitcher(
                        current = mode,
                        onSelect = { target ->
                            navController.navigate(routeOf(target)) {
                                popUpTo(Screen.Calendar.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                    IconButton(onClick = { viewModel.goToNext() }) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "下一个")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.startAddEvent() }) {
                Icon(Icons.Rounded.Add, contentDescription = "新建事件")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (mode) {
                CalendarScreenMode.AGENDA_DAY,
                CalendarScreenMode.AGENDA_WORKDAY,
                CalendarScreenMode.AGENDA_RESTDAY,
                -> {
                    if (agendaDays.isEmpty()) {
                        EmptyViewPlaceholder(text = "这几天没有安排")
                    } else {
                        AgendaStream(
                            days = agendaDays,
                            modifier = Modifier.fillMaxSize(),
                            onItemClick = { item ->
                                agendaDays.forEach { day ->
                                    val detail = day.detail ?: return@forEach
                                    when (item.kind) {
                                        AgendaKind.EVENT -> detail.events
                                            .firstOrNull { "event-${it.id}" == item.key }
                                            ?.let { viewModel.startEditEvent(it) }
                                        AgendaKind.COURSE -> detail.courses
                                            .firstOrNull { "course-${it.id}" == item.key }
                                            ?.let {
                                                navController.navigate(Screen.EditCourse.build(it.id))
                                            }
                                        AgendaKind.TASK -> detail.tasks
                                            .firstOrNull { "task-${it.id}" == item.key }
                                            ?.let {
                                                navController.navigate(Screen.DetailTask.build(it.id))
                                            }
                                    }
                                }
                            },
                        )
                    }
                }

                CalendarScreenMode.MONTH -> Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MonthCalendarGrid(
                        cells = uiState.monthCells,
                        selectedDate = selectedDate,
                        onDateClick = viewModel::onDateClick,
                        lunarLabels = uiState.monthLunarLabels,
                        modifier = Modifier.weight(1f),
                    )
                    MonthTagList(
                        dayDetail = selectedDayDetail,
                        modifier = Modifier.padding(horizontal = AppSpacing.md),
                        onEventClick = { event -> viewModel.startEditEvent(event) },
                        onTaskClick = { task -> navController.navigate(Screen.DetailTask.build(task.id)) },
                        onCourseClick = { course -> navController.navigate(Screen.EditCourse.build(course.id)) },
                        onHabitClick = { showHabitCheckInSheet = true },
                    )
                }

                CalendarScreenMode.WEEK -> WeekCalendarView(
                    timeline = weekTimeline,
                    selectedDate = selectedDate,
                    onDateClick = viewModel::onDateClick,
                    onToggleTask = { task, done -> viewModel.toggleTask(task, done) },
                    modifier = Modifier.fillMaxSize(),
                )

                CalendarScreenMode.DAY -> {
                    val anchor = selectedDate
                    if (anchor != null) {
                        DayCalendarView(
                            date = anchor,
                            dayDetail = selectedDayDetail,
                            modifier = Modifier.fillMaxSize(),
                            overview = todayOverview,
                            onToggleTask = { task, done -> viewModel.toggleTask(task, done) },
                            onBudgetClick = { navController.navigateToTab(Screen.Finance.route) },
                            onTaskClick = { task ->
                                navController.navigate(Screen.DetailTask.build(task.id))
                            },
                            onTaskTimeRangeClick = { task -> viewModel.startEditTaskTimeRange(task) },
                            conflicts = dayConflicts,
                            onHabitCheckIn = { habitId, delta ->
                                viewModel.checkInHabit(habitId, anchor, delta)
                            },
                        )
                    } else {
                        EmptyViewPlaceholder(text = "请回到今天")
                    }
                }
            }
        }

        editingState?.let { state ->
            EditEventSheet(
                state = state,
                availableCategories = eventCategories,
                liveConflict = uiState.liveConflictInfo,
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

/**
 * 视图切换器 —— 导航栏右侧倒数第二个。
 *
 * 菜单里是全部六种视图；当前视图打勾。
 */
@Composable
private fun CalendarViewSwitcher(
    current: CalendarScreenMode,
    onSelect: (CalendarScreenMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Rounded.ViewList, contentDescription = "切换视图")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        CalendarScreenMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.label) },
                onClick = {
                    expanded = false
                    onSelect(mode)
                },
                leadingIcon = {
                    if (mode == current) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    }
}

/** 视图 → 路由 */
private fun routeOf(mode: CalendarScreenMode): String = when (mode) {
    CalendarScreenMode.AGENDA_DAY -> Screen.Calendar.route
    CalendarScreenMode.AGENDA_WORKDAY -> Screen.CalendarWorkday.route
    CalendarScreenMode.AGENDA_RESTDAY -> Screen.CalendarRestday.route
    CalendarScreenMode.MONTH -> Screen.CalendarMonth.route
    CalendarScreenMode.WEEK -> Screen.CalendarWeek.route
    CalendarScreenMode.DAY -> Screen.CalendarDay.route
}

/** 大标题：月份（"11 月"） */
private fun monthTitle(yearMonth: String): String {
    val parts = yearMonth.split("-")
    return if (parts.size == 2) "${parts[1].toInt()} 月" else yearMonth
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
