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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.personalassistant.feature.finance.presentation.components.MonthlyReportCard
import com.example.personalassistant.feature.finance.presentation.components.SearchBar
import com.example.personalassistant.feature.finance.presentation.components.TransactionRow
import com.example.personalassistant.feature.finance.domain.model.AccountBudgetSummary
import com.example.personalassistant.feature.finance.domain.model.BudgetSummaryTotals
import com.example.personalassistant.feature.finance.presentation.components.AccountSummaryCard
import com.example.personalassistant.feature.finance.presentation.transfer.TransferSheet
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import com.example.personalassistant.feature.finance.domain.model.Transaction
import kotlinx.coroutines.launch

/**
 * Phase 7b：Finance 主页
 *
 * 7a → 7b 增量：
 * - 点击 TransactionRow → 打开编辑 Sheet（AddTransactionSheet 复用，editing=tx）
 * - DailyPoolCard 接收 warningThreshold，三档颜色（绿/橙/红）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FinanceScreen(
    navController: NavHostController,
    viewModel: FinanceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchKeyword by viewModel.searchKeyword.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    // Phase 5：跨账户汇总（独立于 uiState 的两条流，避开 combine 的 5 流上限）
    val budgetTotals by viewModel.budgetTotals.collectAsStateWithLifecycle()
    val budgetRows by viewModel.accountBudgetSummaries.collectAsStateWithLifecycle()
    val accountBalances by viewModel.accountBalances.collectAsStateWithLifecycle()
    // 转账行要显示「转出至 / 转入自 哪个账户」；用余额列表做 id→名字映射，
    // 不为此再开一条 observeAccounts（两者本质是同一批账户）
    val accountNameById = remember(accountBalances) {
        accountBalances.associate { it.accountId to it.accountName }
    }
    var showSheet by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var showBudgetSheet by remember { mutableStateOf(false) }
    var showCategoryScreen by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    // Phase 5：转账面板
    var showTransferSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 7b：feedback → Snackbar
    LaunchedEffect(feedback) {
        feedback?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearFeedback()
            }
        }
    }

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
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导入 CSV")
                    }
                    IconButton(onClick = { viewModel.exportTransactions() }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "导出 CSV")
                    }
                    IconButton(onClick = { showBudgetSheet = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "预算设置")
                    }
                    IconButton(onClick = { showCategoryScreen = true }) {
                        Icon(Icons.Filled.Category, contentDescription = "分类管理")
                    }
                    // Phase 5：转账入口。放顶栏而不是 FAB：FAB 是「记一笔收支」，
                    // 转账不是记账而是调拨，两者语义不同不该挤同一个按钮
                    IconButton(onClick = { showTransferSheet = true }) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "账户间转账")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingTx = null
                showSheet = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "记一笔")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        FinanceContent(
            uiState = uiState,
            contentPadding = padding,
            onTransactionClick = { tx ->
                editingTx = tx
                showSheet = true
            },
            onSearchKeywordChange = viewModel::setSearchKeyword,
            searchKeyword = searchKeyword,
            // Phase 5：跨账户总览与转账行的对方账户名
            budgetTotals = budgetTotals,
            budgetRows = budgetRows,
            accountNameById = accountNameById,
        )
    }

    if (showTransferSheet) {
        TransferSheet(
            viewModel = viewModel,
            onDismiss = { showTransferSheet = false },
        )
    }

    if (showSheet) {
        AddTransactionSheet(
            onDismiss = {
                showSheet = false
                editingTx = null
            },
            editing = editingTx,
        )
    }
    if (showBudgetSheet) {
        BudgetSheet(onDismiss = { showBudgetSheet = false })
    }

    // 7b：导入 CSV Dialog（粘贴文本）
    if (showImportDialog) {
        ImportCsvDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { csvText ->
                viewModel.importTransactions(csvText)
                showImportDialog = false
            },
        )
    }
}

@Composable
private fun ImportCsvDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 CSV") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("CSV 内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                maxLines = 10,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun FinanceContent(
    uiState: FinanceUiState,
    contentPadding: PaddingValues,
    onTransactionClick: (Transaction) -> Unit,
    onSearchKeywordChange: (String) -> Unit,
    searchKeyword: String,
    // ===== Phase 5 =====
    budgetTotals: BudgetSummaryTotals,
    budgetRows: List<AccountBudgetSummary>,
    accountNameById: Map<Long, String>,
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
        // 7b：搜索条（注入关键字 + 监听变更）
        item {
            SearchBar(
                keyword = searchKeyword,
                onValueChange = onSearchKeywordChange,
            )
        }

        // 顶部：零钱池卡片（7b 增量：传入 warningThreshold）
        item {
            DailyPoolCard(
                pool = uiState.dailyPool,
                monthBudget = uiState.monthBudget?.totalAmount,
                monthExpense = uiState.monthExpense,
                warningThreshold = uiState.monthBudget?.warningThreshold ?: 0.0,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        // 7b：月度报表卡片（支出 / 收入 / 结余 / Top 5 / 折线）
        item {
            MonthlyReportCard(
                report = uiState.monthReport,
                categoryById = uiState.categoryById,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        // Phase 5：跨账户预算总览（仅 >=2 个账户时显示，判断在卡片内部）
        item {
            AccountSummaryCard(
                totals = budgetTotals,
                rows = budgetRows,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        // 流水列表
        if (uiState.transactions.isEmpty()) {
            item {
                if (searchKeyword.isNotBlank()) {
                    Text(
                        text = "没有匹配「${searchKeyword}」的流水",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                } else {
                    EmptyTransactionsPlaceholder()
                }
            }
        } else {
            items(uiState.transactionsByDay, key = { it.dayMillis }) { day ->
                DaySection(
                    day = day,
                    categoryById = uiState.categoryById,
                    accountNameById = accountNameById,
                    onTransactionClick = onTransactionClick,
                )
            }
        }
    }
}

@Composable
private fun DaySection(
    day: DayGroup,
    categoryById: Map<Long, com.example.personalassistant.feature.finance.domain.model.Category>,
    /** 转账行用（accountId → 账户名）；普通收支行用不到 */
    accountNameById: Map<Long, String> = emptyMap(),
    onTransactionClick: (Transaction) -> Unit,
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
                // Phase 5：转账的对方账户名。拿不到时 TransactionRow 自己降级成不显示自/至
                counterpartyName = tx.transferAccountId?.let { accountNameById[it] },
                onClick = { onTransactionClick(tx) },
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
