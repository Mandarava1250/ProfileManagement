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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.personalassistant.feature.task.presentation.components.RecordExpenseDialog
import com.example.personalassistant.feature.task.presentation.components.TaskMatrixView
import com.example.personalassistant.feature.task.domain.model.ChecklistView
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskListUseCase
import com.example.personalassistant.navigation.Screen
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.example.personalassistant.core.ui.components.BigTitleHeader
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.task.presentation.activeConditionCount
import com.example.personalassistant.feature.task.presentation.components.FilterEntryButton
import java.time.DayOfWeek
import java.time.LocalDate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue

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
    // Phase 21：完成带金额的清单项后弹「记一笔？」
    val pendingExpenseTask by checklistViewModel.pendingExpenseTask.collectAsStateWithLifecycle()
    val expenseCategories by checklistViewModel.expenseCategories.collectAsStateWithLifecycle()
    // Phase 18.7 / 18.8：删除后弹「撤销」（子项被提升为顶层时也在文案里说明）
    val deleteNotice by checklistViewModel.deleteNotice.collectAsStateWithLifecycle()
    // Phase 18.6：多选（长按清单项进入）
    val selectedIds by checklistViewModel.selectedIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 删了就弹一条 Snackbar，点「撤销」放回去；超时/关闭后这条记录作废
    LaunchedEffect(deleteNotice) {
        val notice = deleteNotice ?: return@LaunchedEffect
        val message = buildString {
            if (notice.items.size == 1) {
                append("已删除「${notice.items.first().title}」")
            } else {
                append("已删除 ${notice.items.size} 项")
            }
            // Phase 18.8：子项没被删，只是被 FK SET NULL 提升成了顶层项 —— 得说清楚
            if (notice.promotedChildCount > 0) {
                append("，${notice.promotedChildCount} 个子项已提升为顶层项")
            }
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "撤销",
        )
        if (result == SnackbarResult.ActionPerformed) {
            checklistViewModel.undoDelete()
        } else {
            checklistViewModel.clearDeleteNotice()
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Phase 26：大标题。
    // 多选时标题换成「已选 N 项」—— 多选过程中用户关心的是选了几个。
    val title = if (selectedIds.isNotEmpty()) {
        "已选 ${selectedIds.size} 项"
    } else {
        when (val view = checklistState.currentView) {
            is ChecklistView.Quadrant -> "四象限"
            else -> view.let {
                checklistState.currentTitle.ifBlank {
                    stringResource(id = com.example.personalassistant.R.string.tab_tasks)
                }
            }
        }
    }

    // 副标题：日期 · 星期 · 近 7 天完成率。
    //
    // 口径是「近 7 天」（ObserveCompletionStatsUseCase 的默认窗口就是 7 天），
    // 不能写成「本周」—— 跨周的那几天算不进来，数字会跟用户预期对不上。
    val subtitle = buildString {
        append(DateUtils.formatDate(System.currentTimeMillis()))
        append(" · ")
        append(weekdayLabel(LocalDate.now().dayOfWeek))
        if (!completionStats.isEmpty) {
            append(" · 近 7 天完成率 ${completionStats.completionPercent}%")
        }
    }

    // Phase 26：筛选面板的开关提到 TaskScreen ——
    // 筛选入口按钮跟着大标题搬到了右上角，面板开关状态跟着按钮走。
    var showFilterSheet by remember { mutableStateOf(false) }

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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                BigTitleHeader(
                    title = title,
                    subtitle = subtitle,
                    navigationIcon = {
                        // 抽屉入口 —— 清单页的核心导航，不能删。
                        // 44dp 圆形触达区 + 20dp 图标，跟右上角动作按钮同一套语言。
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { scope.launch { drawerState.open() } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = "打开清单抽屉",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    actions = {
                        // 全局搜索在顶部 —— 跟滴答清单的习惯一致。
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { navController.navigate(Screen.GlobalSearch.route) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "全局搜索",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        // 筛选入口：徽标数字 = 当前生效的筛选条件条数。
                        FilterEntryButton(
                            activeCount = checklistState.filter.activeConditionCount(),
                            contentDescription = "筛选",
                            onClick = { showFilterSheet = true },
                        )
                    },
                )
            },
            floatingActionButton = {
                // C 风格：46dp、14dp 圆角、品牌蓝底。
                // FAB = 详细新建的完整表单；内联输入是快速加一条，两者分工不同。
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(AppRadii.card14))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { navController.navigate(Screen.EditTask.build()) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "新建清单项",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
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
                    // Phase 26：筛选面板的开关在 TaskScreen，
                    // 因为筛选入口按钮跟着大标题搬到了右上角。
                    showFilterSheet = showFilterSheet,
                    onDismissFilterSheet = { showFilterSheet = false },
                    // Phase 22.6：完成率卡片
                    // ===== Phase 18.6：多选 + 批量操作 =====
                    selectedIds = selectedIds,
                    onToggleSelect = checklistViewModel::toggleSelection,
                    onClearSelection = checklistViewModel::clearSelection,
                    onBatchSetDueDate = checklistViewModel::batchSetDueDate,
                    onBatchSetPriority = checklistViewModel::batchSetPriority,
                    onBatchMoveToList = checklistViewModel::batchMoveToList,
                    onBatchAttachTag = checklistViewModel::batchAttachTag,
                    onBatchDelete = checklistViewModel::batchDelete,
                    // ===== Phase 24：左滑 / 右滑单条操作 =====
                    onSetDueDate = checklistViewModel::setItemDueDate,
                    onMoveToList = checklistViewModel::moveItemToList,
                    onSetPinned = checklistViewModel::setItemPinned,
                    )
                }

                // Phase 21：完成带金额的清单项后弹「记一笔？」
                pendingExpenseTask?.let { task ->
                    RecordExpenseDialog(
                        task = task,
                        categories = expenseCategories,
                        onConfirm = { categoryId ->
                            checklistViewModel.confirmExpense(categoryId)
                        },
                        onDismiss = checklistViewModel::dismissExpense,
                    )
                }
            }
        }
    }
}

/** 星期几（中文）—— 大标题副标题用。 */
private fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}
