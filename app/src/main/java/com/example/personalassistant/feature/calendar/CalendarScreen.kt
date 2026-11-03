package com.example.personalassistant.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Tune
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
import com.example.personalassistant.core.ui.components.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.CalendarViewType
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.presentation.CalendarViewModel
import com.example.personalassistant.navigation.Screen
import com.example.personalassistant.navigation.navigateToTab
import com.example.personalassistant.feature.calendar.presentation.EventEditState
import com.example.personalassistant.feature.calendar.presentation.components.DayCalendarView
import com.example.personalassistant.feature.calendar.presentation.components.DayDetailSheet
import com.example.personalassistant.feature.calendar.presentation.components.EditEventSheet
import com.example.personalassistant.feature.calendar.presentation.components.MonthCalendarGrid
import com.example.personalassistant.feature.calendar.presentation.components.MonthTagList
import com.example.personalassistant.feature.calendar.presentation.components.MonthPickerSheet
import com.example.personalassistant.feature.calendar.presentation.components.WeekCalendarView
import com.example.personalassistant.feature.calendar.presentation.components.CalendarDisplaySettingsSheet
import com.example.personalassistant.feature.calendar.presentation.components.TaskTimeRangeSheet
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
        // 性能优化：原先这里还订阅了 viewModel.weekCells，但全工程无人使用它 ——
        // 周视图用的是 weekTimeline（课表风格）。留着这条订阅会白白挂起一条
        // observeMonthSummary(weekStart, weekEndExclusive) 的数据库查询链，故删除。
        val editingState by viewModel.editingState.collectAsStateWithLifecycle()
    val editorError by viewModel.editorError.collectAsStateWithLifecycle()
    val eventCategories by viewModel.eventCategories.collectAsStateWithLifecycle()
    val showCategoryManager by viewModel.showCategoryManager.collectAsStateWithLifecycle()
    val categoryManagerError by viewModel.categoryManagerError.collectAsStateWithLifecycle()
    // Phase 14：日视图顶部的预算横条数据
    val todayOverview by viewModel.todayOverview.collectAsStateWithLifecycle()
    // Phase 14：周视图（课表风格）数据
    val weekTimeline by viewModel.weekTimeline.collectAsStateWithLifecycle()
    // Phase 20.3：日历「显示设置」
    val displayPreferences by viewModel.displayPreferences.collectAsStateWithLifecycle()
    // Phase 20.5：当天「清单项 ↔ 课程」的冲突
    val dayConflicts by viewModel.dayConflicts.collectAsStateWithLifecycle()
    // Phase 20.1：正在编辑时间段的清单项
    val editingTaskTimeRange by viewModel.editingTaskTimeRange.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(editorError) {
        editorError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // Phase 15 P1-2.3：月份选择器的开关
    var showMonthPicker by remember { mutableStateOf(false) }

    // Phase 20.3：日历「显示设置」的开关
    var showDisplaySettings by remember { mutableStateOf(false) }

    // Phase 15 P1-2.3：月份选择器（ModalBottomSheet 走独立 dialog 窗口，放在 Scaffold 前后都行）
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

    // Phase 20.3：日历「显示设置」（课程 / 清单项 / 已完成 / 习惯打卡）
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

    // Phase 20.1：给清单项设置时间段的面板
    editingTaskTimeRange?.let { task ->
        TaskTimeRangeSheet(
            task = task,
            defaultDayStart = selectedDate ?: DateUtils.todayStartMillis(),
            onSave = { startAt, endAt -> viewModel.saveTaskTimeRange(task.id, startAt, endAt) },
            onClear = { viewModel.saveTaskTimeRange(task.id, null, null) },
            onDismiss = { viewModel.clearTaskTimeRangeEditor() },
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(text = headerTitle(viewType, uiState.yearMonth, selectedDate)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goToPrevious() }) {
                        Icon(prevIcon(viewType), contentDescription = "上一个")
                    }
                },
                actions = {

                    // Phase 20.3：日历「显示设置」—— 决定日历里显示课程 / 清单项 / 已完成 / 习惯打卡
                    IconButton(onClick = { showDisplaySettings = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "显示设置")
                    }

                    // Phase 15 P1-2.3：月视图右上角加「选择月份」—— 原来只能一格格挪
                    if (viewType == CalendarViewType.MONTH) {
                        IconButton(onClick = { showMonthPicker = true }) {
                            Icon(Icons.Rounded.DateRange, contentDescription = "选择月份")
                        }
                    }
IconButton(onClick = { viewModel.goToCurrent() }) {
                        Icon(Icons.Rounded.Today, contentDescription = "回到今天")
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
                Icon(Icons.Rounded.Add, contentDescription = "新建事件")
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
                    CalendarViewType.MONTH -> Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        MonthCalendarGrid(
                            cells = uiState.monthCells,
                            selectedDate = selectedDate,
                            onDateClick = viewModel::onDateClick,
                            // Phase 8 Sub-step 9 Step 5：农历角标 map（key = cell.date）
                            lunarLabels = uiState.monthLunarLabels,
                            // Phase 14：网格拿剩余空间（有界高度），不能套 verticalScroll ——
                            // LazyVerticalGrid 在无限高度约束下会直接崩。
                            modifier = Modifier.weight(1f),
                        )
                        // Phase 14：网格下面用标签列出这一天的日程 / 任务 / 课程
                        MonthTagList(
                            dayDetail = selectedDayDetail,
                            modifier = Modifier.padding(horizontal = AppSpacing.md),
                            onEventClick = { event -> viewModel.startEditEvent(event) },
                            // Phase 20：任务 / 课程也能点 —— 之前只有日程能点，
                            // 用户在日历里看到任务却摸不着它。
                            onTaskClick = { task -> navController.navigate(Screen.DetailTask.build(task.id)) },
                            onCourseClick = { course -> navController.navigate(Screen.EditCourse.build(course.id)) },
                        )
                    }
                    CalendarViewType.WEEK -> WeekCalendarView(
                        timeline = weekTimeline,
                        selectedDate = selectedDate,
                        onDateClick = viewModel::onDateClick,
                        onToggleTask = { task, done -> viewModel.toggleTask(task, done) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    CalendarViewType.DAY -> {
                        val anchor = selectedDate
                        if (anchor != null) {
                            // 复用 selectedDayDetail（已经是 selectedDate 派生）
                            DayCalendarView(
                                date = anchor,
                                dayDetail = selectedDayDetail,
                                modifier = Modifier.fillMaxSize(),
                                // Phase 14：预算横条 + 可直接勾选的今日待办
                                overview = todayOverview,
                                onToggleTask = { task, done -> viewModel.toggleTask(task, done) },
                                // Phase 15 P0-1.1：走 navigateToTab —— 之前是裸 navigate，
                                // 会把记账页压在日历之上，导致用户点底栏「日历」回不来
                                onBudgetClick = { navController.navigateToTab(Screen.Finance.route) },
                                // Phase 20.6：点标题跳清单项详情
                                onTaskClick = { task ->
                                    navController.navigate(Screen.DetailTask.build(task.id))
                                },
                                // Phase 20.1：点时钟图标设置清单项的时间段
                                onTaskTimeRangeClick = { task -> viewModel.startEditTaskTimeRange(task) },
                                // Phase 20.5：清单项 ↔ 课程 的冲突提示
                                conflicts = dayConflicts,
                            )
                        } else {
                            EmptyViewPlaceholder(text = "请回到今天")
                        }
                    }
                }
            }
        }

        // Phase 14：月视图改成「网格 + 标签列表」后，不再弹 DayDetailSheet ——
        // 标签就在网格下面，多一层弹窗反而挡住视线。
        // Sub-step 5：EditEventSheet（事件编辑器）
        editingState?.let { state ->
            EditEventSheet(
                state = state,
                availableCategories = eventCategories,
                // Phase 8 Sub-step 9 Step 4.2：实时冲突（debounce 后）
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
        // Phase 14：周视图标题显示选中日的完整日期 + 星期几，
        // 让「今天在哪一天」一眼就有答案，不用去数日期栏。
        if (selectedDate == null) "本周"
        else weekRangeLabel(selectedDate)
    }
    CalendarViewType.DAY -> {
        if (selectedDate == null) "今日"
        else DateUtils.formatChineseDate(selectedDate)
    }
}

/**
 * 周范围标题（周视图用）
 *
 * 整周视图下，标题说「这一周是几号到几号」比说某一天更有用 ——
 * 哪一天是星期几，星期头部那一行已经写得清清楚楚了。
 */
private fun weekRangeLabel(date: Long): String {
    val zone = ZoneId.systemDefault()
    val (weekStart, weekEndExclusive) = CalendarViewModel.computeWeekRange(date)
    val start = Instant.ofEpochMilli(weekStart).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(weekEndExclusive - 1).atZone(zone).toLocalDate()
    return if (start.monthValue == end.monthValue) {
        "${start.monthValue}月${start.dayOfMonth}日 - ${end.dayOfMonth}日"
    } else {
        "${start.monthValue}月${start.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"
    }
}

private fun prevIcon(viewType: CalendarViewType) = when (viewType) {
    CalendarViewType.MONTH -> Icons.Rounded.ChevronLeft
    CalendarViewType.WEEK -> Icons.Rounded.ChevronLeft
    CalendarViewType.DAY -> Icons.Rounded.ChevronLeft
}

private fun nextIcon(viewType: CalendarViewType) = when (viewType) {
    CalendarViewType.MONTH -> Icons.Rounded.ChevronRight
    CalendarViewType.WEEK -> Icons.Rounded.ChevronRight
    CalendarViewType.DAY -> Icons.Rounded.ChevronRight
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
