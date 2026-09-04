package com.example.personalassistant.feature.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.presentation.TimetableViewModel
import com.example.personalassistant.feature.timetable.presentation.components.DayHeaderRow
import com.example.personalassistant.feature.timetable.presentation.components.SemesterSelector
import com.example.personalassistant.feature.timetable.presentation.components.TimetableGrid
import com.example.personalassistant.feature.timetable.presentation.components.WeekHeaderRow
import com.example.personalassistant.navigation.Screen
import java.time.LocalDate

/**
 * 课表主页
 *
 *  - 顶部 AppBar：学期选择 + 第几周
 *  - 中部：周一-周日日期头
 *  - 主体：7 列课程网格
 *  - 底部 FAB：新建课程
 *  - Snackbar：处理 errorMessage
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    navController: NavHostController,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

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
                TopAppBar(
                    title = { Text("课表") },
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
            FloatingActionButton(onClick = { navController.navigate(Screen.EditCourse.build()) }) {
                Icon(Icons.Filled.Add, contentDescription = "新增课程")
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
                    onAddSemester = {
                        // 简化版，未提供学期新增 UI
                        navController.navigate(Screen.EditCourse.build())
                    },
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

            DayHeaderRow(
                weekStart = LocalDate.now().minusDays(((LocalDate.now().dayOfWeek.value - 1).toLong())),
            )

            HorizontalDivider(thickness = 0.5.dp)

            Spacer(Modifier.height(4.dp))

            // 按 dayOfWeek 分组
            val coursesByDay: Map<Int, List<Course>> = (1..7).associateWith { day ->
                state.courses.filter { it.dayOfWeek == day }
            }

            TimetableGrid(
                coursesByDay = coursesByDay,
                onCourseClick = { course -> navController.navigate(Screen.EditCourse.build(course.id)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EmptySemesterHint(onAddSemester: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "还没有学期",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "点击右下角 + 添加你的第一门课程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}