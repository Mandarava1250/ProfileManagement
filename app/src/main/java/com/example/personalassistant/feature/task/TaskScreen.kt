package com.example.personalassistant.feature.task

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.feature.task.presentation.ChecklistTab
import com.example.personalassistant.feature.task.presentation.ChecklistViewModel
import com.example.personalassistant.feature.task.presentation.TasksTab
import com.example.personalassistant.feature.task.presentation.TaskViewModel
import com.example.personalassistant.navigation.Screen

/**
 * 任务主页（Phase 3 实现）
 *
 * - 顶部 TabRow：任务 / 清单 两个子页
 * - 任务 Tab：P1-P4 + 未排期 四象限 + 已完成折叠区
 * - 清单 Tab：独立清单项（无 Task 的纯清单模式）
 * - FAB 在"任务" Tab：跳到编辑页新建任务；"清单" Tab 用顶部输入框
 * - 点任务卡 → 编辑页
 *
 * Phase 10 修复：FAB 在嵌套 Scaffold 下被外层 BottomBar 遮挡的问题
 *
 * 外层 [AppNavHost] 的 Scaffold 自己挂了 BottomBar；本任务的 Scaffold 是内层
 * Scaffold，没有 BottomBar。Material 3 Scaffold 把 FAB 用 `Modifier.align(BottomEnd)`
 * 钉到 **自己** 高度区域右下角——而这个区域刚好就是外层 BottomBar 占用的位置，
 * 导致 FAB 被 BottomBar 完全盖住，截图里看不到 "+" 按钮。
 *
 * 修复：放弃 `floatingActionButton` slot，直接把 FAB 放进 content 的 Box 里，
 * 用 `Modifier.align(Alignment.BottomEnd)` 定位 + 16dp 内边距，与 BottomBar
 * 上方保持安全距离。这样 FAB 一定在 BottomBar 之上、永远可见可点击。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    navController: NavHostController,
    taskViewModel: TaskViewModel = hiltViewModel(),
    checklistViewModel: ChecklistViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val taskState by taskViewModel.uiState.collectAsStateWithLifecycle()
    val checklistState by checklistViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(id = com.example.personalassistant.R.string.tab_tasks)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("任务") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("清单") },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> TasksTab(
                    state = taskState,
                    onToggleComplete = taskViewModel::toggleComplete,
                    onClickTask = { id -> navController.navigate(Screen.DetailTask.build(id)) },
                    onDelete = taskViewModel::deleteTask,
                    onToggleShowCompleted = taskViewModel::toggleShowCompleted,
                    onFilterChange = { filter -> taskViewModel.setFilter(filter) },
                    onTagToggle = taskViewModel::toggleTagFilter,
                    onClearTag = taskViewModel::clearTagFilter,
                    onSortClick = taskViewModel::toggleSortMenu,
                    onSortSelected = taskViewModel::setSort,
                    onSortDismiss = taskViewModel::dismissSortMenu,
                    // ===== Phase 8b' =====
                    onShowAll = taskViewModel::showAll,
                    onSelectQuadrant = taskViewModel::selectQuadrant,
                    onAliasChange = taskViewModel::setAlias,
                    // ===== Phase 8final G2 =====
                    onShowMatrix = taskViewModel::showMatrix,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> ChecklistTab(
                    state = checklistState,
                    onToggleComplete = checklistViewModel::toggleComplete,
                    onDelete = checklistViewModel::deleteItem,
                    onAdd = checklistViewModel::addItem,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Phase 10 修复：FAB 不再放进 Scaffold 的 floatingActionButton slot，
            // 而是放到 content Box 里手动 BottomEnd 对齐。这样:
            // 1. FAB 永远位于 BottomBar 上方 16dp，不会被外层 BottomBar 盖住
            // 2. 清单 Tab 上不显示（保持原有行为：清单 Tab 用顶部输入框）
            // 3. 桌面宽屏 / 手机窄屏都用相同偏移，无需额外响应式处理
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(Screen.EditTask.build())
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建任务",
                    )
                }
            }
        }
    }
}