package com.example.personalassistant.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.feature.calendar.presentation.CalendarViewModel
import com.example.personalassistant.feature.calendar.presentation.components.MonthCalendarGrid

/**
 * Phase 7c：日历主页（重写原占位实现）
 *
 * - 顶部 TopAppBar：左/右月份切换 + 中间 yyyy 年 M 月 + "回到今天"按钮
 * - 中间：6×7 月视图（MonthCalendarGrid）
 * - 底部：DayDetailSheet（Sub-step 4 实现）
 *
 * @param navController 保留参数供 Sub-step 4/5 导航使用（暂时 unused）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavHostController,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = uiState.yearMonthDisplay) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.goToPrevMonth() }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.goToToday() },
                        enabled = !uiState.isShowingCurrentMonth,
                    ) {
                        Icon(Icons.Filled.Today, contentDescription = "回到今天")
                    }
                    IconButton(onClick = { viewModel.goToNextMonth() }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MonthCalendarGrid(
                cells = uiState.monthCells,
                selectedDate = selectedDate,
                onDateClick = viewModel::onDateClick,
                modifier = Modifier.fillMaxWidth(),
            )

            // DayDetailSheet 占位（Sub-step 4 实装）
            if (selectedDate != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Sub-step 4 将在这里展示 DayDetailSheet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}