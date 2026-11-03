package com.example.personalassistant.feature.task

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.core.ui.components.AppTopBar
import com.example.personalassistant.feature.task.presentation.ChecklistTab
import com.example.personalassistant.feature.task.presentation.ChecklistViewModel
import com.example.personalassistant.feature.task.presentation.TaskViewModel
import com.example.personalassistant.feature.task.presentation.components.ChecklistDrawer
import com.example.personalassistant.feature.task.presentation.components.TaskMatrixView
import com.example.personalassistant.feature.task.domain.model.ChecklistView
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskListUseCase
import com.example.personalassistant.navigation.Screen
import kotlinx.coroutines.launch

/**
 * 清单主页（Phase 17 重构）
 *
 * ## Phase 17 变化：从「双 Tab」到「单页 + 抽屉」
 *
 * 之前顶部有「任务 / 清单」两个子 Tab —— 两套模型两套页面，用户得自己记
 * 「提醒在哪加、金额在哪填」。Phase 16 把模型合并成一套之后，双 Tab 就没必要了：
 *
 * - 顶部只留一个标题（当前视图名）
 * - 左侧抽屉装**智能清单**（收集箱 / 今天 / 最近 7 天 / 全部 / 已完成）
 *   与**用户清单**（自建的归属地，带未完成数徽标）
 * - 内容区统一是清单项列表
 *
 * Phase 10 修复（仍然有效）：FAB 归位到 Scaffold slot —— 外层 AppNavHost 已把
 * BottomBar 的 padding 下传给 NavHost，所以 slot 天然落在底栏之上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    navController: NavHostController,
    checklistViewModel: ChecklistViewModel = hiltViewModel(),
    taskViewModel: TaskViewModel = hiltViewModel(),
) {
    val checklistState by checklistViewModel.uiState.collectAsStateWithLifecycle()
    // 四象限视图用 TaskViewModel 的数据（矩阵 + 显示偏好）
    val taskState by taskViewModel.uiState.collectAsStateWithLifecycle()
    // Phase 19：命名过滤器（抽屉里的「筛选器」段）
    val namedFilters by checklistViewModel.namedFilters.collectAsStateWithLifecycle()
    // Phase 22.6：完成率统计（清单页顶部那张卡片）
    val completionStats by checklistViewModel.completionStats.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChecklistDrawer(
                currentView = checklistState.currentView,
                lists = checklistState.lists,
                onSelect = { view ->
                    checklistViewModel.selectView(view)
                    scope.launch { drawerState.close() }
                },
                onCreateList = { name -> checklistViewModel.createList(name) },
                onRenameList = { id, name -> checklistViewModel.renameList(id, name) },
                onDeleteList = { id ->
                    checklistViewModel.deleteList(
                        id = id,
                        // 任务移到收集箱，不连带删除 —— 用户的东西不该悄悄消失
                        strategy = DeleteTaskListUseCase.DeleteStrategy.MOVE_TASKS_TO_INBOX,
                    )
                },
                // ===== Phase 19：筛选器 =====
                namedFilters = namedFilters,
                onApplyNamedFilter = { filter ->
                    checklistViewModel.applyNamedFilter(filter)
                    scope.launch { drawerState.close() }
                },
                onDeleteNamedFilter = { name -> checklistViewModel.deleteNamedFilter(name) },
                onSaveNamedFilter = { name -> checklistViewModel.saveNamedFilter(name) },
                // Phase 23：习惯页入口
                onOpenHabits = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.Habits.route)
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = {
                        Text(
                            text = when (val view = checklistState.currentView) {
                                is ChecklistView.Quadrant -> "四象限"
                                else -> view.let {
                                    checklistState.currentTitle.ifBlank {
                                        stringResource(id = com.example.personalassistant.R.string.tab_tasks)
                                    }
                                }
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "打开清单抽屉",
                            )
                        }
                    },
                    actions = {
                        // Phase 22.1：全局搜索入口
                        // 滴答清单的搜索在顶部 —— 这里跟它的习惯一致，
                        // 而不是把入口藏进「更多」里让用户找不到。
                        IconButton(onClick = { navController.navigate(Screen.GlobalSearch.route) }) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "全局搜索",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(Screen.EditTask.build())
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "新建清单项",
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // 四象限视图：换排布，不是筛选 —— 数据来自 TaskViewModel
                if (checklistState.currentView == ChecklistView.Quadrant) {
                    TaskMatrixView(
                        matrix = taskState.matrix,
                        displayPreferences = taskState.displayPreferences,
                        onToggleComplete = taskViewModel::toggleComplete,
                        onClickTask = { id -> navController.navigate(Screen.DetailTask.build(id)) },
                        onDelete = taskViewModel::deleteTask,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ChecklistTab(
                    state = checklistState,
                    onToggleComplete = checklistViewModel::toggleComplete,
                    onDelete = checklistViewModel::deleteItem,
                    onAdd = checklistViewModel::addItem,
                    // Phase 15：新建（id == null）/ 更新（id != null）走同一个入口
                    onSaveItem = { edit ->
                        val id = edit.id
                        if (id == null) {
                            checklistViewModel.addItemWithDetails(
                                title = edit.title,
                                category = edit.category,
                                dueDate = edit.dueDate,
                                priority = edit.priority,
                                amount = edit.amount,
                            )
                        } else {
                            checklistViewModel.updateItem(
                                id = id,
                                title = edit.title,
                                category = edit.category,
                                dueDate = edit.dueDate,
                                priority = edit.priority,
                                amount = edit.amount,
                            )
                        }
                    },
                    // 清空可空字段（分类 / 截止时间 / 金额）必须走这条 —— updateItem 的 null 是「不改」
                    onClearItemFields = checklistViewModel::clearItemFields,
                    onAttachTag = checklistViewModel::attachTag,
                    onDetachTag = checklistViewModel::detachTag,
                    onAddSubTask = checklistViewModel::addSubTask,
                    onCategoryFilterChange = checklistViewModel::setCategoryFilter,
                    onTagFilterChange = checklistViewModel::setTagFilter,
                    onGroupChange = checklistViewModel::setGroup,
                    onSortChange = checklistViewModel::setSort,
                    onResetFilter = checklistViewModel::resetFilter,
                    modifier = Modifier.fillMaxSize(),
                    // Phase 22.6：完成率卡片
                    completionStats = completionStats,
                    )
                }
            }
        }
    }
}
