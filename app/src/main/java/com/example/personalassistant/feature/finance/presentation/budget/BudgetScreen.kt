package com.example.personalassistant.feature.finance.presentation.budget

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Phase 13：预算设置独立页
 *
 * 之前预算是记账页右上角的一个底部弹窗（[BudgetSheet]），
 * 设置页想放「预算设置」入口时跳不过去 —— 弹窗不是导航目的地。
 *
 * 提成独立页后：记账页继续用弹窗（不打断「记一笔」的上下文），
 * 设置页用整页（长期设置该有个完整页面）。
 * 两边共用 [BudgetForm]，字段与保存行为一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预算设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        BudgetForm(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            viewModel = viewModel,
            title = null,
            onSaveDone = onBack,
        )
    }
}
