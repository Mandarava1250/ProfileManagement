package com.example.personalassistant.feature.task

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
 * 任务主页（Phase 1 占位实现）
 *
 * 后续 Phase 3 将替换为：
 * - 按日期分组的任务列表（今天/明天/未来/已完成）
 * - 底部快速添加输入框
 * - 优先级筛选与排序
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(navController: NavHostController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("任务") }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("任务模块 — Phase 3 实现")
        }
    }
}
