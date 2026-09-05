package com.example.personalassistant.feature.task

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = {
                    navController.navigate(Screen.EditTask.build())
                }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建任务",
                    )
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> TasksTab(
                state = taskState,
                onToggleComplete = taskViewModel::toggleComplete,
                onEditTask = { id -> navController.navigate(Screen.EditTask.build(id)) },
                onDelete = taskViewModel::deleteTask,
                onToggleShowCompleted = taskViewModel::toggleShowCompleted,
                onFilterChange = taskViewModel::setFilter,
                onSortClick = taskViewModel::toggleSortMenu,
                onSortSelected = taskViewModel::setSort,
                onSortDismiss = taskViewModel::dismissSortMenu,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> ChecklistTab(
                state = checklistState,
                onToggleComplete = checklistViewModel::toggleComplete,
                onDelete = checklistViewModel::deleteItem,
                onAdd = checklistViewModel::addItem,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}
