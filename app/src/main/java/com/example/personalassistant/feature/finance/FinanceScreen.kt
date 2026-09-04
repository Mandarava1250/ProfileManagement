package com.example.personalassistant.feature.finance

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
 * 记账主页（Phase 1 占位实现）
 *
 * 后续 Phase 4 将替换为：
 * - 零钱池卡片（顶部常驻）
 * - 当日交易列表
 * - 快速记账按钮（弹 BottomSheet）
 * - 月度统计图表（饼图/折线图）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(navController: NavHostController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("记账") }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("记账模块（含零钱池）— Phase 4 实现")
        }
    }
}
