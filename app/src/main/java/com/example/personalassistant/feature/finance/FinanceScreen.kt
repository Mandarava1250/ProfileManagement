package com.example.personalassistant.feature.finance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.feature.finance.presentation.DayGroup
import com.example.personalassistant.feature.finance.presentation.FinanceUiState
import com.example.personalassistant.feature.finance.presentation.FinanceViewModel
import com.example.personalassistant.feature.finance.presentation.add.AddTransactionSheet
import com.example.personalassistant.feature.finance.presentation.budget.BudgetSheet
import com.example.personalassistant.feature.finance.presentation.category.CategoryManagementScreen
import com.example.personalassistant.feature.finance.presentation.components.DailyPoolCard
import com.example.personalassistant.feature.finance.presentation.components.TransactionRow
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat

/**
 * Phase 7a：Finance 主页
 *
 * 顶层入口：TopBar + 滚动列表 + FAB
 * - 顶部 DailyPoolCard
 * - 中部：按日分组的流水列表
 * - FAB → AddTransactionSheet
 * - TopBar 菜单：预算 / 分类管理
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FinanceScreen(
    navController: NavHostController,
    viewModel: FinanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var showBudgetSheet by remember { mutableStateOf(false) }
    var showCategoryScreen by remember { mutableStateOf(false) }

    // 分类管理页是独立全屏
    if (showCategoryScreen) {
        CategoryManagementScreen(onBack = { showCategoryScreen = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记账") },
                actions = {
                    IconButton(onClick = { showBudgetSheet = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "预算设置")
                    }
                    IconButton(onClick = { showCategoryScreen = true }) {
                        Icon(Icons.Filled.Category, contentDescription = "分类管理")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "记一笔")
            }
        },
    ) { padding ->
        FinanceContent(
            uiState = uiState,
            contentPadding = padding,
        )
    }

    if (showAddSheet) {
        AddTransactionSheet(onDismiss = { showAddSheet = false })
    }
    if (showBudgetSheet) {
        BudgetSheet(onDismiss = { showBudgetSheet = false })
    }
}

@Composable
private fun FinanceContent(
    uiState: FinanceUiState,
    contentPadding: PaddingValues,
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("加载中…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 顶部：零钱池卡片
        item {
            DailyPoolCard(
                pool = uiState.dailyPool,
                monthBudget = uiState.monthBudget?.totalAmount,
                monthExpense = uiState.monthExpense,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        // 流水列表
        if (uiState.transactionsByDay.isEmpty()) {
            item {
                EmptyTransactionsPlaceholder()
            }
        } else {
            items(uiState.transactionsByDay, key = { it.dayMillis }) { day ->
                DaySection(
                    day = day,
                    categoryById = uiState.categoryById,
                )
            }
        }
    }
}

@Composable
private fun DaySection(
    day: DayGroup,
    categoryById: Map<Long, com.example.personalassistant.feature.finance.domain.model.Category>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 日期 + 当日收支小计
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = FinanceDateUtils.formatRelativeDate(day.dayMillis),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (day.totalExpense > 0) {
                    Text(
                        text = "支 ${MoneyFormat.format(day.totalExpense)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (day.totalIncome > 0) {
                    Text(
                        text = "收 ${MoneyFormat.format(day.totalIncome)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        day.transactions.forEach { tx ->
            TransactionRow(
                transaction = tx,
                category = categoryById[tx.categoryId],
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun EmptyTransactionsPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "还没有流水",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = "点击右下角 + 记一笔",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
