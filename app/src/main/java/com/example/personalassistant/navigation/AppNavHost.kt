package com.example.personalassistant.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
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
import com.example.personalassistant.feature.settings.backup.BackupPreviewScreen
import com.example.personalassistant.feature.task.TaskScreen
import com.example.personalassistant.feature.task.presentation.detail.TaskDetailScreen
import com.example.personalassistant.feature.task.presentation.edit.TaskEditScreen
import com.example.personalassistant.feature.timetable.TimetableScreen
import com.example.personalassistant.feature.timetable.presentation.edit.CourseEditScreen

/**
 * 全局导航图：BottomBar 承载 4 个主 Tab + 设置页作为隐式入口
 *
 * 各 Screen 的详细实现位于 feature/<module>/presentation/ 目录下
 *
 * **通知 deeplink 处理（plan 8final §2.1 v2）**：
 * - [pendingNavRoute] 由 [com.example.personalassistant.MainActivity] 写入
 * - `LaunchedEffect(pendingNavRoute)` 监听值变化 → 校验 → `navController.navigate`
 * - **route 白名单**：仅允许已注册的 `Screen.route` 前缀，防止恶意 Intent 跳到未注册路径
 */
@Composable
fun AppNavHost(pendingNavRoute: MutableState<String?>? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in BottomTab.entries.map { it.screen.route }

    // === 通知 deeplink 消费：监听 pendingNavRoute，命中白名单则 navigate ===
    val pendingRoute = pendingNavRoute?.value
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        if (!isKnownDeepLinkRoute(route)) {
            return@LaunchedEffect
        }
        navController.navigate(route) {
            // 保留 BottomBar 起始栈（让用户能 navigate 回去），不 saveState 避免与 BottomBar 状态冲突
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = false
                inclusive = false
            }
            launchSingleTop = true
        }
        // 消费后清空，防止 recomposition 重复触发
        pendingNavRoute?.value = null
    }

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

            // === Phase 8 Sub-step 1 日程编辑页（plan 8final 补完：之前未注册导致 GlobalSearch 跳不过去）===
            composable(
                route = Screen.EditEvent.route,
                arguments = listOf(navArgument(Screen.EditEvent.ARG_EVENT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }),
            ) { entry ->
                val eventId = entry.arguments?.getLong(Screen.EditEvent.ARG_EVENT_ID) ?: -1L
                com.example.personalassistant.feature.calendar.presentation.edit.EventEditScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() },
                    onOpenInCalendar = {
                        // 跳到 Calendar 主屏（popBackStack + navigate 重置 BottomBar 选中态）
                        navController.popBackStack()
                        navController.navigate(Screen.Calendar.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = false
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
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

            // === Phase 8 Sub-step 4'（plan 8f'）：备份预览 + 三档策略导入 ===
            composable(Screen.BackupPreview.route) {
                BackupPreviewScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * 通知 deeplink 路由白名单（plan 8final §2.1 v2 要求）
 *
 * **只** 接受已注册到 NavHost 的 route 前缀。新增 Screen 时必须同步更新此处。
 *
 * **避免 hack**：用硬编码前缀字面量，不用 `removeSuffix("{xxx}")` —— 后者一旦
 * 模板占位符改名会误判。
 */
private fun isKnownDeepLinkRoute(route: String): Boolean {
    return route == Screen.Timetable.route ||
        route == Screen.Tasks.route ||
        route == Screen.Finance.route ||
        route == Screen.Calendar.route ||
        route == Screen.Settings.route ||
        route == Screen.GlobalSearch.route ||
        route == Screen.BackupPreview.route ||
        route.startsWith("timetable/edit/") ||
        route.startsWith("tasks/edit/") ||
        route.startsWith("tasks/detail/") ||
        route.startsWith("calendar/edit/")
}

@Composable
private fun AppBottomBar(
    navController: NavHostController,
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

/** 局部类型别名，避免引入 androidx.navigation.NavHostController 顶层 import */
private typealias NavHostController = androidx.navigation.NavHostController