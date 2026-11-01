package com.example.personalassistant.feature.timetable

import androidx.compose.foundation.layout.Arrangement
import com.example.personalassistant.core.ui.components.EmptyState
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.example.personalassistant.core.ui.components.AppTopBar
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
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.presentation.TimetableViewModel
import com.example.personalassistant.feature.timetable.presentation.components.DayHeaderRow
import com.example.personalassistant.feature.timetable.presentation.components.SemesterCreateDialog
import com.example.personalassistant.feature.timetable.presentation.components.SemesterSelector
import com.example.personalassistant.feature.timetable.presentation.components.TIMETABLE_AXIS_WIDTH
import com.example.personalassistant.feature.timetable.presentation.components.TimetableGrid
import com.example.personalassistant.feature.timetable.presentation.components.WeekHeaderRow
import com.example.personalassistant.navigation.Screen
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 课表主页
 *
 *  - 顶部 AppBar：学期选择 + 第几周 + 右上角「导入课表」入口
 *  - 中部：周一-周日日期头
 *  - 主体：7 列课程网格
 *  - 底部 FAB：新建课程
 *  - Snackbar：处理 errorMessage / 导入提示
 *
 * Phase 10：TopAppBar 加 actions slot 放导入按钮
 *
 * 之前 AppBar 只有 title，没有 actions 槽，用户找不到怎么批量导入课表（从教务系统 /
 * ICS 文件 / Excel 等导入）。补一个 `Icons.Rounded.FileUpload` 图标按钮作为入口，
 * 后续接解析逻辑；现在点这条接入。点击后通过已有 SnackbarHost 给个友好提示，
 * 这样 UI 占位和后续实现可以分开演进。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    navController: NavHostController,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val importHint = stringResource(R.string.timetable_action_import_coming_soon)

    // Phase 10 修复 2：学期创建对话框状态。无学期时"点 +"会进这里；
    // 有学期时 SemesterSelector 菜单底部也能触发新学期。
    var showCreateSemester by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    title = { Text(stringResource(R.string.tab_timetable)) },
                    actions = {
                        // Phase 10：右上角导入入口 —— 之前 AppBar 只有左侧 title，actions 槽空着。
                        // 用 FileUpload 图标，点击后弹个 Snackbar 提示"开发中"，等真正接 ICS / 教务
                        // 抓取实现时直接替换 onClick 即可，不影响布局。
                        IconButton(
                            onClick = {
                                // Phase 10：先弹一个"开发中"提示占位（用户能立刻看到点击有反馈）。
                                // 未来接 ICS / 教务抓取时，这里替换成
                                // `navController.navigate(Screen.ImportCourse.route)` 或
                                // `viewModel.requestImport()` 即可，UI 入口形状不变。
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(importHint)
                                }
                            },
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = stringResource(R.string.timetable_action_import),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        // Phase 11：右上角 ⚙️ 入口 → 跳到课表设置页（学期编辑 + 课时表编辑）
                        IconButton(
                            onClick = { navController.navigate(Screen.TimetableSettings.route) },
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "课表设置",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SemesterSelector(
                        current = state.currentSemester,
                        semesters = state.semesters,
                        onSelect = { viewModel.switchSemester(it) },
                        onRequestCreate = { showCreateSemester = true },
                    )
                    if (state.activeCoursesCount > 0) {
                        Text(
                            text = "本周 ${state.activeCoursesCount} 节课",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        },
        floatingActionButton = {
            // Phase 10 修复 2：学期为空时按 + 不让进 EditCourse（没学期→课程建出来
            // 观察流也查不到，等于白建），强制走学期创建流程；有学期才进课程编辑。
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
                // Phase 10 修复 2：原来 FAB 跳 EditCourse 是错的——没学期时 Course.semester 拿不到
                // 任何有效值，保存到数据库后会被 observeBySemester 过滤掉、UI 上"加完不显示"。
                // 修正路径：FAB 改为"创建学期"弹窗；学期建好之后下方的"+ 新建课程"才出现。
                EmptySemesterHint(
                    onCreateSemester = { showCreateSemester = true },
                )
                return@Column
            }

            WeekHeaderRow(
                weekNumber = state.currentWeekNumber,
                isCurrentWeek = true, // TODO: 拿到用户实际是否在手动选周
                onPrevWeek = { viewModel.selectWeek(state.currentWeekNumber - 1) },
                onNextWeek = { viewModel.selectWeek(state.currentWeekNumber + 1) },
                onJumpToCurrent = { viewModel.jumpToCurrentWeek() },
            )

            Spacer(Modifier.height(4.dp))

            // 周几表头必须与下方 TimetableGrid 的列严格对齐。
            //
            // TimetableGrid 是「左 TIMETABLE_AXIS_WIDTH 时间轴 + 右 7 列 weight=1f 均分剩余宽度」，
            // 而 DayHeaderRow 原本整宽均分 7 列 —— 两者横向基准差一个轴宽，
            // 导致标签与实际列错位（周一被推到最右、标签被切一半）。
            //
            // 修法（A2）：外层 Row 左边补一个等宽 Spacer，
            // 让 DayHeaderRow 只占「剩余宽度」，与 grid 的 7 列共享同一套宽度基准。
            // 轴宽常量从 TimetableGrid 导出，两边引用同一个值，杜绝以后再漂。
            // 本周起点：应用存活期间保持不变 —— remember 一次即可，
            // 免得每次重组都取两次当前时间再算一遍周偏移
            val currentWeekStart = remember {
                LocalDate.now().minusDays(((LocalDate.now().dayOfWeek.value - 1).toLong()))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(Modifier.width(TIMETABLE_AXIS_WIDTH))
                DayHeaderRow(
                    weekStart = currentWeekStart,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(thickness = 0.5.dp)

            Spacer(Modifier.height(4.dp))

            // 按 dayOfWeek 分组：课程列表没变时分组结果也不会变，缓存住，
            // 免得每次重组都重建 7 组 filter + 7 个新 List
            val coursesByDay: Map<Int, List<Course>> = remember(state.courses) {
                (1..7).associateWith { day ->
                    state.courses.filter { it.dayOfWeek == day }
                }
            }

            TimetableGrid(
                coursesByDay = coursesByDay,
                onCourseClick = { course -> navController.navigate(Screen.EditCourse.build(course.id)) },
                timeSlotSchedule = state.timeSlotSchedule,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Phase 10 修复 2：学期创建对话框（弹窗式 + FAB 入口共用）。
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
                Button(onClick = onCreateSemester) {
                    Text(stringResource(R.string.timetable_create_semester))
                }
            },
        )
    }
}