package com.example.personalassistant.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.personalassistant.feature.calendar.CalendarScreen
import com.example.personalassistant.feature.finance.FinanceScreen
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchHit
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchHitType
import com.example.personalassistant.feature.globalsearch.presentation.GlobalSearchScreen
import com.example.personalassistant.feature.settings.SettingsScreen
import com.example.personalassistant.feature.task.TaskScreen
import com.example.personalassistant.feature.task.presentation.detail.TaskDetailScreen
import com.example.personalassistant.feature.task.presentation.edit.TaskEditScreen
import com.example.personalassistant.feature.timetable.TimetableScreen
import com.example.personalassistant.feature.timetable.presentation.edit.CourseEditScreen

/**
 * 全局导航图：BottomBar 承载 4 个主 Tab + 设置页作为隐式入口
 *
 * 各 Screen 的详细实现位于 feature/<module>/presentation/ 目录下
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in BottomTab.entries.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = BottomTab.Timetable.screen.route,
            modifier = Modifier.padding(PaddingValues())
        ) {
            // === 4 个主 Tab ===
            composable(BottomTab.Timetable.screen.route) { TimetableScreen(navController) }
            composable(BottomTab.Tasks.screen.route) { TaskScreen(navController) }
            composable(BottomTab.Finance.screen.route) { FinanceScreen(navController) }
            composable(BottomTab.Calendar.screen.route) { CalendarScreen(navController) }

            // === 二级页面 ===
            composable(BottomTab.SETTINGS.first.route) { SettingsScreen(navController) }

            // === Phase 2 课表编辑页 ===
            composable(
                route = Screen.EditCourse.route,
                arguments = listOf(navArgument(Screen.EditCourse.ARG_COURSE_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }),
            ) { entry ->
                val courseId = entry.arguments?.getLong(Screen.EditCourse.ARG_COURSE_ID) ?: -1L
                CourseEditScreen(courseId = courseId, onBack = { navController.popBackStack() })
            }

            // === Phase 3 任务编辑页 ===
            composable(
                route = Screen.EditTask.route,
                arguments = listOf(navArgument(Screen.EditTask.ARG_TASK_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }),
            ) { entry ->
                val taskId = entry.arguments?.getLong(Screen.EditTask.ARG_TASK_ID) ?: -1L
                TaskEditScreen(taskId = taskId, onBack = { navController.popBackStack() })
            }

            // === Phase 6b 任务详情页 ===
            composable(
                route = Screen.DetailTask.route,
                arguments = listOf(navArgument(Screen.DetailTask.ARG_TASK_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }),
            ) { entry ->
                val taskId = entry.arguments?.getLong(Screen.DetailTask.ARG_TASK_ID) ?: -1L
                TaskDetailScreen(
                    taskId = taskId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Screen.EditTask.build(id)) },
                )
            }

            // === Phase 8 Sub-step 3 全局搜索 ===
            composable(Screen.GlobalSearch.route) {
                GlobalSearchScreen(
                    onBack = { navController.popBackStack() },
                    onHitClick = { hit ->
                        // 根据命中类型跳转对应详情页
                        when (hit.type) {
                            GlobalSearchHitType.TASK ->
                                navController.navigate(Screen.DetailTask.build(hit.entityId))
                            GlobalSearchHitType.EVENT ->
                                navController.navigate(Screen.EditEvent.build(hit.entityId))
                            GlobalSearchHitType.COURSE ->
                                navController.navigate(Screen.EditCourse.build(hit.entityId))
                            GlobalSearchHitType.TRANSACTION -> {
                                // 流水没有专门详情页，先回到 Finance Tab（占位）
                                navController.popBackStack()
                                navController.navigate(Screen.Finance.route)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(
    navController: androidx.navigation.NavHostController,
    currentRoute: String?
) {
    NavigationBar {
        BottomTab.entries.forEach { tab ->
            val selected = currentRoute == tab.screen.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != tab.screen.route) {
                        navController.navigate(tab.screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.labelRes)) }
            )
        }
    }
}
