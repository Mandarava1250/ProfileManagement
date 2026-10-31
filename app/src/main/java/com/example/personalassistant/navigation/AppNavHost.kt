package com.example.personalassistant.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.feature.calendar.CalendarScreen
import com.example.personalassistant.feature.finance.FinanceScreen
import com.example.personalassistant.feature.finance.presentation.budget.BudgetScreen
import com.example.personalassistant.feature.finance.presentation.category.CategoryManagementScreen
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchHitType
import com.example.personalassistant.feature.globalsearch.presentation.GlobalSearchScreen
import com.example.personalassistant.feature.me.MeScreen
import com.example.personalassistant.feature.me.presentation.GpaRuleEditScreen
import com.example.personalassistant.feature.settings.SettingsScreen
import com.example.personalassistant.feature.settings.appearance.AppearanceScreen
import com.example.personalassistant.feature.settings.backup.BackupPreviewScreen
import com.example.personalassistant.feature.task.TaskScreen
import com.example.personalassistant.feature.task.presentation.detail.TaskDetailScreen
import com.example.personalassistant.feature.task.presentation.edit.TaskEditScreen
import com.example.personalassistant.feature.timetable.TimetableScreen
import com.example.personalassistant.feature.timetable.presentation.edit.CourseEditScreen
import com.example.personalassistant.feature.timetable.presentation.settings.TimetableSettingsScreen

/**
 * 全局导航图（Phase 14：底栏 4 Tab + 「更多」面板）
 *
 * 各 Screen 的详细实现位于 feature/<module>/presentation/ 目录下
 *
 * **底栏结构**
 * - 4 个高频功能 Tab：日历 / 任务 / 课表 / 记账，图标 + 文字标签常显
 * - 第 5 个位置：「更多」→ 在底栏上方弹出 ModalBottomSheet，里面是「我」和「设置」
 * - 「我」「设置」不再是 Tab，但**路由保留**（deeplink 白名单里的 me / settings 仍然有效）
 *
 * **底栏可见性**
 * 底栏在这 6 个 route 上都显示（4 个 Tab + 我 + 设置）。
 * 从「更多」进入「我」「设置」后底栏还在，用户可以直接切走 ——
 * 这是 Phase 13 之前就存在的坑（「我」是 Tab 时底栏判定用 BottomTab.entries，
 * 一旦「我」「设置」移出 BottomTab，这条判定就会让底栏消失）。
 *
 * **通知 deeplink 处理（plan 8final §2.1 v2）**：
 * - [pendingNavRoute] 由 [com.example.personalassistant.MainActivity] 写入
 * - `LaunchedEffect(pendingNavRoute)` 监听值变化 → 校验 → `navController.navigate`
 * - **route 白名单**：仅允许已注册的 `Screen.route` 前缀，防止恶意 Intent 跳到未注册路径
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost(pendingNavRoute: MutableState<String?>? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = isBottomBarRoute(currentRoute)

    // 「更多」面板的开关状态。放这里而不是底栏组件内部，
    // 是因为 ModalBottomSheet 要渲染在 Scaffold 内容层之上，不能塞进 bottomBar slot。
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }

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
                AppBottomBar(
                    navController = navController,
                    currentRoute = currentRoute,
                    onMoreClick = { showMoreSheet = true },
                )
            }
        }
    ) { innerPadding ->
        // Phase 10 修复：把外层 Scaffold 的 padding（包含 BottomBar 高度）真正下传给 NavHost。
        // 之前写的是 `Modifier.padding(PaddingValues())`（空值），导致 NavHost 区域延伸到
        // 屏幕底部、与 BottomBar 重叠，内层各页的 FAB（课表 / 任务）都被 BottomBar 盖住。
        NavHost(
            navController = navController,
            // Phase 14：首屏改为日历 —— 日历的「日」视图就是「今日」，符合今日优先的思路
            startDestination = BottomTab.Calendar.screen.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            // === 4 个主 Tab + 「我」「设置」（后两者从更多面板进入）===
            composable(BottomTab.Calendar.screen.route) { CalendarScreen(navController) }
            composable(BottomTab.Tasks.screen.route) { TaskScreen(navController) }
            composable(BottomTab.Timetable.screen.route) { TimetableScreen(navController) }
            composable(BottomTab.Finance.screen.route) { FinanceScreen(navController) }
            composable(Screen.Me.route) { MeScreen(navController) }
            composable(Screen.Settings.route) { SettingsScreen(navController) }

            // === 二级页面 ===

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
            ) { entry ->                val eventId = entry.arguments?.getLong(Screen.EditEvent.ARG_EVENT_ID) ?: -1L
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

            // === Phase 11：课表设置（学期编辑 + 课时表编辑）===
            composable(Screen.TimetableSettings.route) {
                TimetableSettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // === Phase 12：GPA 规则编辑（学校定制）===
            composable(Screen.EditGpaRule.route) {
                GpaRuleEditScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // === Phase 14：外观设置（主题模式 / 主题色 / 背景）===
            composable(Screen.Appearance.route) {
                AppearanceScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // === Phase 13：记账子页（独立 route）===
            //
            // 这两页在记账页里本来是「同页替换」/「底部弹窗」，
            // 设置页要放入口时跳不过去 —— 弹窗不是导航目的地。
            // 提成独立 route 后两边共用同一套实现：
            //  - 分类管理：记账页仍走原来的同页替换，设置页跳过来是独立页
            //  - 预算设置：记账页仍用底部弹窗，设置页跳过来是整页
            composable(Screen.FinanceCategory.route) {
                CategoryManagementScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.FinanceBudget.route) {
                BudgetScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    // === 「更多」面板 ===
    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
        ) {
            MoreMenu(
                onNavigate = { route ->
                    showMoreSheet = false
                    navController.navigate(route) {
                        // 「我」「设置」是二级页，不是 Tab —— 不走 Tab 的 saveState 策略，
                        // 跟通知 deeplink 用同一套：回到起始栈但保留底栏可切走。
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = false
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

/**
 * 底栏可见的 route 白名单
 *
 * 4 个主 Tab + 「我」+ 「设置」。后两者虽然不再是 Tab，但进入后底栏要留着，
 * 否则用户点进「我」就找不到返回主界面的路（系统返回键能退，但视觉上没有出口）。
 *
 * Phase 14 阶段七：改成 internal 并配一个 [isBottomBarRoute]，
 * 让 JVM 单测（BottomBarRoutesTest）能直接断言这张白名单，
 * 而不是把实现复制到测试里（复制出来的版本迟早会漂移）。
 */
internal val BOTTOM_BAR_ROUTES = setOf(
    Screen.Calendar.route,
    Screen.Tasks.route,
    Screen.Timetable.route,
    Screen.Finance.route,
    Screen.Me.route,
    Screen.Settings.route,
)

/**
 * 某个 route 要不要显示底栏。
 *
 * - null（还没有 back stack entry）/ 空串 → false
 * - 命中 [BOTTOM_BAR_ROUTES] → true
 */
internal fun isBottomBarRoute(route: String?): Boolean =
    route != null && route in BOTTOM_BAR_ROUTES

/**
 * 通知 deeplink 路由白名单（plan 8final §2.1 v2 要求）
 *
 * **只**接受已注册到 NavHost 的 route 前缀。新增 Screen 时必须同步更新此处。
 *
 * **避免 hack**：用硬编码前缀字面量，不用 `removeSuffix("{xxx}")` —— 后者一旦
 * 模板占位符改名会误判。
 */
private fun isKnownDeepLinkRoute(route: String): Boolean {
    return route == Screen.Timetable.route ||
        route == Screen.Tasks.route ||
        route == Screen.Finance.route ||
        route == Screen.Calendar.route ||
        route == Screen.Me.route ||
        route == Screen.Settings.route ||
        route == Screen.GlobalSearch.route ||
        route == Screen.BackupPreview.route ||
        route.startsWith("timetable/edit/") ||
        route.startsWith("tasks/edit/") ||
        route.startsWith("tasks/detail/") ||
        route.startsWith("calendar/edit/") ||
        route == Screen.EditGpaRule.route
}

/**
 * 底部导航栏：4 个功能 Tab + 「更多」
 *
 * Phase 14：文字标签**常显**（alwaysShowLabel = true）。
 * 4 项每格 90dp，11sp 的两个汉字（20dp）绰绰有余，不用再靠猜图标区分。
 */
@Composable
internal fun AppBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    onMoreClick: () -> Unit,
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
                // 文字标签已经承担了语义，图标 contentDescription 设 null 避免读屏重复朗读
                icon = { Icon(tab.icon, contentDescription = null) },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                alwaysShowLabel = true,
            )
        }

        // 第 5 个位置：「更多」
        val moreSelected = currentRoute == Screen.Me.route || currentRoute == Screen.Settings.route
        NavigationBarItem(
            selected = moreSelected,
            onClick = onMoreClick,
            icon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = null) },
            label = {
                Text(
                    text = stringResource(R.string.tab_more),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            alwaysShowLabel = true,
        )
    }
}

/**
 * 「更多」面板的内容：我 / 设置
 */
@Composable
internal fun MoreMenu(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_more),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
        )
        MoreMenuItem(
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.tab_me),
            subtitle = stringResource(R.string.me_subtitle),
            onClick = { onNavigate(Screen.Me.route) },
        )
        MoreMenuItem(
            icon = Icons.Rounded.Settings,
            title = stringResource(R.string.tab_settings),
            subtitle = stringResource(R.string.settings_subtitle),
            onClick = { onNavigate(Screen.Settings.route) },
        )
    }
}

@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.card))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(AppRadii.chip))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 局部类型别名，避免引入 androidx.navigation.NavHostController 顶层 import */
private typealias NavHostController = androidx.navigation.NavHostController
