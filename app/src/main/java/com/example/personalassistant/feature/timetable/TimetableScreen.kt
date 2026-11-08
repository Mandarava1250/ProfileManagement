package com.example.personalassistant.feature.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.BigTitleHeader
import com.example.personalassistant.core.ui.components.EmptyState
import com.example.personalassistant.core.ui.components.SectionLabel
import com.example.personalassistant.core.ui.components.UnderlineTabs
import com.example.personalassistant.core.ui.theme.AppPageSubtitle
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.presentation.TimetableViewModel
import com.example.personalassistant.feature.timetable.presentation.TimetableViewType
import com.example.personalassistant.feature.timetable.presentation.components.DayHeaderRow
import com.example.personalassistant.feature.timetable.presentation.components.SemesterCreateDialog
import com.example.personalassistant.feature.timetable.presentation.components.SemesterDropdownMenu
import com.example.personalassistant.feature.timetable.presentation.components.SemesterSelector
import com.example.personalassistant.feature.timetable.presentation.components.TIMETABLE_AXIS_WIDTH
import com.example.personalassistant.feature.timetable.presentation.components.TimetableGrid
import com.example.personalassistant.feature.timetable.presentation.components.TimetableTimelineView
import com.example.personalassistant.feature.timetable.presentation.components.WeekHeaderRow
import com.example.personalassistant.navigation.Screen
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 课表主页
 *
 * ## Phase 26：双视图
 *
 * 原来只有「整周网格」一个视图。现在拆成两个标签：
 *
 * - **时间线**：一天一列到底，课程名 / 地点 / 教师都读得全（`TimetableTimelineView`）
 * - **整周网格**：7 列横滑，看一周的分布与空档（`TimetableGrid`）
 *
 * 整周网格下面还挂一段「今日课程」竖排列表 —— 网格那张图看的是分布，
 * 真要确认「今天几点在哪上课」，竖排列表才读得舒服。
 *
 * ## 横向滚动为什么表头和网格共用一个 ScrollState
 *
 * 网格是 7 列 × 60dp 固定宽（合计 420dp，比屏幕宽），表头也是 7 × 60dp。
 * 两者必须共用同一个 `ScrollState`，否则滑网格时星期标签停在原地，对不上。
 *
 * @see com.example.personalassistant.feature.timetable.presentation.components.TimetableGrid
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    navController: NavHostController,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Phase 26：视图类型与时间线选中的星期（都是独立 StateFlow）
    val viewType by viewModel.viewType.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val importHint = stringResource(R.string.timetable_action_import_coming_soon)

    // 学期创建对话框状态。无学期时"点 +"会进这里；有学期时 SemesterSelector 菜单底部也能触发新学期。
    var showCreateSemester by remember { mutableStateOf(false) }

    // 表头与网格共用的横向滚动状态
    val hScroll = rememberScrollState()

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // 副标题：学期 · 第 N 周 · 本周 N 节
    val subtitle = buildString {
        state.currentSemester?.name?.let { append(it).append(" · ") }
        append("第 ${state.currentWeekNumber} 周")
        if (state.activeCoursesCount > 0) {
            append(" · 本周 ${state.activeCoursesCount} 节")
        }
    }

    // 副标题末尾那个「⌄」：
    // 只有一个学期时没有可切换的对象，箭头就不画 —— 摆个点不动的箭头骗人。
    // 展开时换成「⌃」，让用户知道菜单已经开了。
    val canSwitchSemester = state.semesters.size > 1
    var semesterMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                BigTitleHeader(
                    title = stringResource(R.string.tab_timetable),
                    // 副标题末尾挂一个活的学期下拉：
                    // 字符随展开状态变（⌄ / ⌃），点了真能开菜单。
                    subtitleContent = {
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = subtitle,
                                    style = AppPageSubtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                if (canSwitchSemester) {
                                    Text(
                                        text = if (semesterMenuOpen) " ⌃" else " ⌄",
                                        style = AppPageSubtitle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { semesterMenuOpen = true }
                                            .padding(horizontal = 4.dp),
                                    )
                                }
                            }
                            SemesterDropdownMenu(
                                expanded = semesterMenuOpen,
                                semesters = state.semesters,
                                current = state.currentSemester,
                                onSelect = { viewModel.switchSemester(it) },
                                onRequestCreate = { showCreateSemester = true },
                                onDismissRequest = { semesterMenuOpen = false },
                            )
                        }
                    },
                    actions = {
                        // 导入入口：点一下弹 Snackbar 提示「开发中」，
                        // 未来接 ICS / 教务抓取时在这里替换 onClick 即可，UI 入口形状不变。
                        IconButton(onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(importHint)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = stringResource(R.string.timetable_action_import),
                            )
                        }
                        // 课表设置页（学期编辑 + 课时表编辑）
                        IconButton(onClick = { navController.navigate(Screen.TimetableSettings.route) }) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "课表设置",
                            )
                        }
                    },
                )

                // 学期切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SemesterSelector(
                        current = state.currentSemester,
                        semesters = state.semesters,
                        onSelect = { viewModel.switchSemester(it) },
                        onRequestCreate = { showCreateSemester = true },
                    )
                }

                // 双视图切换
                UnderlineTabs(
                    tabs = TimetableViewType.ordered.map { it.label },
                    selectedIndex = TimetableViewType.ordered.indexOf(viewType).coerceAtLeast(0),
                    onSelect = { index -> viewModel.setViewType(TimetableViewType.ordered[index]) },
                    modifier = Modifier.padding(horizontal = AppSpacing.md),
                )
            }
        },
        floatingActionButton = {
            // 学期为空时按 + 不进 EditCourse（没学期 → 课程建出来观察流也查不到，等于白建），
            // 强制走学期创建流程；有学期才进课程编辑。
            FloatingActionButton(
                onClick = {
                    if (state.semesters.isEmpty()) {
                        showCreateSemester = true
                    } else {
                        navController.navigate(Screen.EditCourse.build())
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = if (state.semesters.isEmpty())
                        "新建学期"
                    else stringResource(R.string.timetable_add_course),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.semesters.isEmpty()) {
                EmptySemesterHint(
                    onCreateSemester = { showCreateSemester = true },
                )
                return@Column
            }

            WeekHeaderRow(
                weekNumber = state.currentWeekNumber,
                // Phase 26 修：之前写死 true，「回到本周」按钮永远不出现，
                // 用户手动翻到别的周之后回不去。
                isCurrentWeek = state.isCurrentWeek,
                onPrevWeek = { viewModel.selectWeek(state.currentWeekNumber - 1) },
                onNextWeek = { viewModel.selectWeek(state.currentWeekNumber + 1) },
                onJumpToCurrent = { viewModel.jumpToCurrentWeek() },
            )

            // 按 dayOfWeek 分组：课程列表没变时分组结果也不会变，缓存住，
            // 免得每次重组都重建 7 组 filter + 7 个新 List
            val coursesByDay: Map<Int, List<Course>> = remember(state.courses) {
                (1..7).associateWith { day ->
                    state.courses.filter { it.dayOfWeek == day }
                }
            }

            // 本周起点：应用存活期间保持不变 —— remember 一次即可，
            // 免得每次重组都取两次当前时间再算一遍周偏移
            val currentWeekStart = remember {
                LocalDate.now().minusDays(((LocalDate.now().dayOfWeek.value - 1).toLong()))
            }
            val todayDayOfWeek = remember { LocalDate.now().dayOfWeek.value }

            when (viewType) {
                TimetableViewType.TIMELINE -> {
                    TimetableTimelineView(
                        coursesByDay = coursesByDay,
                        selectedDay = selectedDay,
                        onSelectDay = { viewModel.selectDay(it) },
                        onCourseClick = { course ->
                            navController.navigate(Screen.EditCourse.build(course.id))
                        },
                        weekStart = currentWeekStart,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                TimetableViewType.WEEK_GRID -> {
                    // 周几表头必须与下方网格的列严格对齐：
                    // 左边补一个等宽 Spacer（时间轴不随横向滚动），
                    // 右边 7 列与网格共用同一个 horizontalScroll。
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.width(TIMETABLE_AXIS_WIDTH))
                        Row(modifier = Modifier.horizontalScroll(hScroll)) {
                            DayHeaderRow(weekStart = currentWeekStart)
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp)

                    TimetableGrid(
                        coursesByDay = coursesByDay,
                        onCourseClick = { course ->
                            navController.navigate(Screen.EditCourse.build(course.id))
                        },
                        timeSlotSchedule = state.timeSlotSchedule,
                        currentDayOfWeek = todayDayOfWeek,
                        hScrollState = hScroll,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 网格看的是分布，「今天几点在哪上课」还得靠竖排列表读
                    SectionLabel(title = "今日课程")
                    TimetableTimelineView(
                        coursesByDay = coursesByDay,
                        selectedDay = todayDayOfWeek,
                        onSelectDay = { },
                        onCourseClick = { course ->
                            navController.navigate(Screen.EditCourse.build(course.id))
                        },
                        weekStart = currentWeekStart,
                        showDateStrip = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // 学期创建对话框（弹窗式 + FAB 入口共用）。
    // 成功创建后 VM 会写入数据库并 switchSemester，state.semesters 状态自动刷新，
    // empty-state 分支会因为学期非空而被跳过。
    if (showCreateSemester) {
        SemesterCreateDialog(
            onDismiss = { showCreateSemester = false },
            onConfirm = { name, start, end ->
                viewModel.addSemester(name, start, end, makeCurrent = true)
                showCreateSemester = false
            },
        )
    }
}

@Composable
private fun EmptySemesterHint(onCreateSemester: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            illustration = R.drawable.ic_empty_timetable,
            title = stringResource(R.string.empty_timetable_title),
            description = stringResource(R.string.empty_timetable_desc),
            action = {
                androidx.compose.material3.Button(onClick = onCreateSemester) {
                    Text(stringResource(R.string.timetable_create_semester))
                }
            },
        )
    }
}
