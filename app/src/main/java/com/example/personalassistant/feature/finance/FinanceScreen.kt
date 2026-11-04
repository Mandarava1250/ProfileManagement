package com.example.personalassistant.feature.finance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
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
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.AppTopBar
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
import com.example.personalassistant.feature.finance.presentation.components.CoinPoolCard
import com.example.personalassistant.feature.finance.presentation.components.DailyPoolCard
import com.example.personalassistant.feature.finance.presentation.components.MonthlyReportCard
import com.example.personalassistant.feature.finance.presentation.components.SearchBar
import com.example.personalassistant.feature.finance.presentation.components.TransactionRow
import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import com.example.personalassistant.feature.finance.domain.model.AccountBudgetSummary
import com.example.personalassistant.feature.finance.domain.model.BudgetSummaryTotals
import com.example.personalassistant.feature.finance.domain.model.CoinPool
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingResult
import com.example.personalassistant.feature.finance.presentation.components.AccountSummaryCard
import com.example.personalassistant.feature.finance.presentation.transfer.TransferSheet
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.profile.presentation.accountswitcher.AccountSwitcherBar
import com.example.personalassistant.feature.profile.presentation.accountswitcher.AccountSwitcherState
import com.example.personalassistant.feature.profile.presentation.accountswitcher.AccountSwitcherViewModel
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
    // Phase 9b B2：账户切换条独立 VM。账户清单/当前账户与记账数据不是同一批流，
    // 塞进 FinanceViewModel 会撞上 combine 的 5 流上限，也会让「切账户失败」
    // 和「转账余额不足」共用同一条 feedback 通道。
    accountSwitcherViewModel: AccountSwitcherViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accountSwitcherState by accountSwitcherViewModel.state.collectAsStateWithLifecycle()
    val searchKeyword by viewModel.searchKeyword.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    // Phase 5：跨账户汇总（独立于 uiState 的两条流，避开 combine 的 5 流上限）
    val budgetTotals by viewModel.budgetTotals.collectAsStateWithLifecycle()
    val budgetRows by viewModel.accountBudgetSummaries.collectAsStateWithLifecycle()
    val accountBalances by viewModel.accountBalances.collectAsStateWithLifecycle()
    // 月截止日策略：月报卡片要显示账期区间（"本月"可能不是自然月）
    val cutoffPolicy by viewModel.cutoffPolicy.collectAsStateWithLifecycle()
    // 批次 C：「今日剩余」派生结果 + 零钱池（都是独立 StateFlow，避开 uiState 的 5 流上限）
    val todayRemaining by viewModel.todayRemainingResult.collectAsStateWithLifecycle()
    val coinPool by viewModel.coinPool.collectAsStateWithLifecycle()
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

    // Phase 9b B2：切账户失败走独立提示通道（消费后清空，避免重建时重放）
    LaunchedEffect(accountSwitcherState.errorMessage) {
        accountSwitcherState.errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                accountSwitcherViewModel.consumeError()
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
            AppTopBar(
                title = { Text(stringResource(R.string.finance_title)) },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = "导入 CSV")
                    }
                    IconButton(onClick = { viewModel.exportTransactions() }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "导出 CSV")
                    }
                    IconButton(onClick = { showBudgetSheet = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "预算设置")
                    }
                    IconButton(onClick = { showCategoryScreen = true }) {
                        Icon(Icons.Rounded.Category, contentDescription = "分类管理")
                    }
                    // Phase 5：转账入口。放顶栏而不是 FAB：FAB 是「记一笔收支」，
                    // 转账不是记账而是调拨，两者语义不同不该挤同一个按钮
                    IconButton(onClick = { showTransferSheet = true }) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = "账户间转账")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingTx = null
                showSheet = true
            }) {
                Icon(Icons.Rounded.Add, contentDescription = "记一笔")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        FinanceContent(
            uiState = uiState,
            contentPadding = padding,
            cutoffPolicy = cutoffPolicy,
            // 批次 C
            todayRemaining = todayRemaining,
            coinPool = coinPool,
            onToggleRemainingMode = {
                viewModel.setTodayRemainingMode(
                    if (todayRemaining.mode == TodayRemainingMode.FIXED) {
                        TodayRemainingMode.AUTO
                    } else {
                        TodayRemainingMode.FIXED
                    }
                )
            },
            onAdjustCoinPool = { delta, note -> viewModel.adjustCoinPool(delta, note) },
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
            // Phase 9b B2：顶部账户切换条
            accountSwitcherState = accountSwitcherState,
            onSwitchAccount = accountSwitcherViewModel::switchTo,
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
    // ===== Phase 9b B2 =====
    accountSwitcherState: AccountSwitcherState,
    onSwitchAccount: (Long) -> Unit,
    // ===== 月截止日 =====
    cutoffPolicy: MonthCutoffPolicy,
    // ===== 批次 C =====
    todayRemaining: TodayRemainingResult,
    coinPool: CoinPool?,
    onToggleRemainingMode: () -> Unit,
    onAdjustCoinPool: (Double, String?) -> Unit,
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Phase 9b B2：账户切换条（单账户 / 加载中时组件内部自动不渲染）
        item {
            AccountSwitcherBar(
                state = accountSwitcherState,
                onSwitch = onSwitchAccount,
                // LazyColumn 的 item 已有 horizontal padding，这里去掉避免双重缩进
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // 7b：搜索条（注入关键字 + 监听变更）
        item {
            SearchBar(
                keyword = searchKeyword,
                onValueChange = onSearchKeywordChange,
            )
        }

        // 顶部：今日剩余卡片（批次 C：大字号改成纯计算，按口径二选一）
        item {
            DailyPoolCard(
                result = todayRemaining,
                monthBudget = uiState.monthBudget?.totalAmount,
                monthExpense = uiState.monthExpense,
                warningThreshold = uiState.monthBudget?.warningThreshold ?: 0.0,
                onToggleMode = onToggleRemainingMode,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        // 批次 C：零钱池卡片（与「今日剩余」平级，标题 / 副标题 / 图标都区分开）
        item {
            CoinPoolCard(
                pool = coinPool,
                onAdjust = onAdjustCoinPool,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        // 7b：月度报表卡片（支出 / 收入 / 结余 / Top 5 / 折线）
        item {
            MonthlyReportCard(
                report = uiState.monthReport,
                categoryById = uiState.categoryById,
                modifier = Modifier.padding(vertical = 4.dp),
                billingRangeText = BillingCycle.displayRange(
                    uiState.monthReport?.yearMonth ?: FinanceDateUtils.currentYearMonth(),
                    cutoffPolicy,
                ),
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