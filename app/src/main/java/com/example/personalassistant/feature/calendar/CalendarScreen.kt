package com.example.personalassistant.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController

/**
 * 日历主页（Phase 1 占位实现）
 *
 * 后续 Phase 5 将替换为：
 * - 月/周/日视图切换
 * - 当日事件列表
 * - 添加/编辑事件
 * - 与任务、课表联动
 * - 节假日显示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavHostController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("日历") }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("日历模块 — Phase 5 实现")
        }
    }
}
