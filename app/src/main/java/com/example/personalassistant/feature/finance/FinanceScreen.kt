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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.BigTitleHeader
import com.example.personalassistant.core.ui.components.SectionLabel
import com.example.personalassistant.core.ui.components.UnderlineTabs
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import com.example.personalassistant.feature.finance.domain.model.AccountBudgetSummary
import com.example.personalassistant.feature.finance.domain.model.BudgetSummaryTotals
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.CoinPool
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingResult
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.presentation.DayGroup
import com.example.personalassistant.feature.finance.presentation.FinanceTab
import com.example.personalassistant.feature.finance.presentation.FinanceUiState
import com.example.personalassistant.feature.finance.presentation.FinanceViewModel
import com.example.personalassistant.feature.finance.presentation.add.AddTransactionSheet
import com.example.personalassistant.feature.finance.presentation.budget.BudgetSheet
import com.example.personalassistant.feature.finance.presentation.category.CategoryManagementScreen
import com.example.personalassistant.feature.finance.presentation.components.AccountSummaryCard
import com.example.personalassistant.feature.finance.presentation.components.CoinPoolCard
import com.example.personalassistant.feature.finance.presentation.components.DailyPoolCard
import com.example.personalassistant.feature.finance.presentation.components.MonthlyReportCard
import com.example.personalassistant.feature.finance.presentation.components.SearchBar
import com.example.personalassistant.feature.finance.presentation.components.TransactionRow
import com.example.personalassistant.feature.finance.presentation.transfer.TransferSheet
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import com.example.personalassistant.feature.profile.presentation.accountswitcher.AccountSwitcherState
import com.example.personalassistant.feature.profile.presentation.accountswitcher.AccountSwitcherViewModel
import kotlinx.coroutines.launch

/**
 * 记账主页（Phase 7b → Phase 26 重画）
 *
 * ## Phase 26 的结构变化
 *
 * 改造前六块内容纵向堆在一个滚动列表里：账户切换条 → 搜索 → 今日剩余 →
 * 零钱池 → 月报 → 跨账户预算 → 流水。一屏之内滚很久才看得到流水，
 * 而「今天还能花多少」和「这个月花在哪」本来就是两种关注点，堆在一起谁也不突出。
 *
 * 现在拆成四个标签（见 [FinanceTab]），每屏只讲一件事：
 *
 * | 标签 | 内容 |
 * |---|---|
 * | 流水 | 搜索 + 按天分组的交易列表（最高频） |
 * | 报表 | 月度收支 / 分类 Top 5 / 折线 |
 * | 零钱池 | 日终结转累加的钱（只读） |
 * | 预算 | 跨账户预算总览 |
 *
 * **今日剩余卡在四个标签下都在**：它是记账页的主数字，「今天还能花多少」
 * 不该因为切去看报表就消失。
 *
 * ## 账户切换去哪了
 *
 * 原来的 `AccountSwitcherBar` 是一条横向 chip 条，在暖白大标题下显得很重。
 * 现在账户名进了副标题，切换入口收成大标题右侧的账户图标（单账户时整颗隐藏）。
 * 功能一个没少，但不再占一整行高度。
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
    // Phase 26：首页四标签（独立 StateFlow，理由见 FinanceViewModel.selectTab）
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
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
    // Phase 26：顶栏「更多」菜单（导入 / 导出 / 预算设置）
    var showMoreMenu by remember { mutableStateOf(false) }
    // Phase 26：账户切换下拉
    var showAccountMenu by remember { mutableStateOf(false) }
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

    // 副标题：当前账户名 · 共 N 个账户。
    //
    // 只读 accountSwitcherState，不猜「日常钱包」这种假名 —— 账户清单还没回来时
    // 就说「记账」，不要先画一个可能不对的账户名。
    val accountSubtitle = remember(accountSwitcherState) {
        val current = accountSwitcherState.accounts
            .firstOrNull { it.id == accountSwitcherState.currentAccountId }
        when {
            accountSwitcherState.isLoading -> null
            current == null -> null
            accountSwitcherState.accounts.size > 1 ->
                "${current.name} · 共 ${accountSwitcherState.accounts.size} 个账户"
            else -> current.name
        }
    }

    Scaffold(
        topBar = {
            Column {
                BigTitleHeader(
                    title = stringResource(R.string.finance_title),
                    subtitle = accountSubtitle,
                    actions = {
                        // ===== 账户切换 =====
                        // 单账户时整颗隐藏 —— 一个只有「它自己」的菜单没有信息量。
                        if (accountSwitcherState.accounts.size > 1) {
                            Box {
                                IconButton(onClick = { showAccountMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountCircle,
                                        contentDescription = "切换账户",
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAccountMenu,
                                    onDismissRequest = { showAccountMenu = false },
                                ) {
                                    accountSwitcherState.accounts.forEach { account ->
                                        val selected =
                                            account.id == accountSwitcherState.currentAccountId
                                        DropdownMenuItem(
                                            text = { Text(account.name) },
                                            leadingIcon = {
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showAccountMenu = false
                                                accountSwitcherViewModel.switchTo(account.id)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // Phase 5：转账入口。放顶栏而不是 FAB：FAB 是「记一笔收支」，
                        // 转账不是记账而是调拨，两者语义不同不该挤同一个按钮
                        IconButton(onClick = { showTransferSheet = true }) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = "账户间转账",
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        IconButton(onClick = { showCategoryScreen = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Category,
                                contentDescription = "分类管理",
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // 导入 / 导出 / 预算设置收进「更多」。
                        //
                        // 改造前这五颗图标并排塞在顶栏，换成 26sp 大标题后横向空间
                        // 不够用了（5 × 48dp + 标题 = 超出 360dp）。低频动作收进菜单，
                        // 高频的转账和分类留在外面。
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "更多",
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导入 CSV") },
                                    onClick = {
                                        showMoreMenu = false
                                        showImportDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("导出 CSV") },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.exportTransactions()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("预算设置") },
                                    onClick = {
                                        showMoreMenu = false
                                        showBudgetSheet = true
                                    },
                                )
                            }
                        }
                    },
                )

                UnderlineTabs(
                    tabs = FinanceTab.ordered.map { it.label },
                    selectedIndex = FinanceTab.ordered.indexOf(selectedTab).coerceAtLeast(0),
                    onSelect = { index -> viewModel.selectTab(FinanceTab.ordered[index]) },
                    modifier = Modifier.padding(horizontal = AppSpacing.md),
                )
            }
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
            selectedTab = selectedTab,
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
            onOpenBudgetSettings = { showBudgetSheet = true },
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

/**
 * 记账内容区。
 *
 * 用一个 LazyColumn 按标签切换 item —— 而不是给每个标签各开一个 LazyColumn。
 * 这样切换标签时滚动位置会重置（符合预期：换了一屏内容），
 * 也避免四个列表各自持有一份 state。
 */
@Composable
private fun FinanceContent(
    uiState: FinanceUiState,
    selectedTab: FinanceTab,
    contentPadding: PaddingValues,
    onTransactionClick: (Transaction) -> Unit,
    onSearchKeywordChange: (String) -> Unit,
    searchKeyword: String,
    // ===== Phase 5 =====
    budgetTotals: BudgetSummaryTotals,
    budgetRows: List<AccountBudgetSummary>,
    accountNameById: Map<Long, String>,
    // ===== 月截止日 =====
    cutoffPolicy: MonthCutoffPolicy,
    // ===== 批次 C =====
    todayRemaining: TodayRemainingResult,
    coinPool: CoinPool?,
    onToggleRemainingMode: () -> Unit,
    // ===== Phase 26 =====
    onOpenBudgetSettings: () -> Unit,
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
        contentPadding = PaddingValues(
            start = AppSpacing.md,
            end = AppSpacing.md,
            top = AppSpacing.sm,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        // ===== 今日剩余：四个标签共用 =====
        // 它是记账页的主数字，「今天还能花多少」不该因为切去看报表就消失。
        item(key = "daily-pool") {
            DailyPoolCard(
                result = todayRemaining,
                monthBudget = uiState.monthBudget?.totalAmount,
                monthExpense = uiState.monthExpense,
                warningThreshold = uiState.monthBudget?.warningThreshold ?: 0.0,
                onToggleMode = onToggleRemainingMode,
            )
        }

        when (selectedTab) {
            // ==================== 流水 ====================
            FinanceTab.LEDGER -> {
                item(key = "search") {
                    SearchBar(
                        keyword = searchKeyword,
                        onValueChange = onSearchKeywordChange,
                    )
                }

                if (uiState.transactions.isEmpty()) {
                    item(key = "empty") {
                        if (searchKeyword.isNotBlank()) {
                            Text(
                                text = "没有匹配「$searchKeyword」的流水",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppSpacing.xl),
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

            // ==================== 报表 ====================
            FinanceTab.REPORT -> {
                item(key = "report") {
                    MonthlyReportCard(
                        report = uiState.monthReport,
                        categoryById = uiState.categoryById,
                        billingRangeText = BillingCycle.displayRange(
                            uiState.monthReport?.yearMonth
                                ?: FinanceDateUtils.currentYearMonth(),
                            cutoffPolicy,
                        ),
                    )
                }
            }

            // ==================== 零钱池 ====================
            FinanceTab.COIN_POOL -> {
                item(key = "coin-pool") {
                    CoinPoolCard(pool = coinPool)
                }
            }

            // ==================== 预算 ====================
            FinanceTab.BUDGET -> {
                item(key = "budget-edit") {
                    // 预算标签自己的入口：这里才是「改预算」最自然的位置，
                    // 顶栏菜单里那个是给习惯从顶栏找的人留的。
                    TextButton(onClick = onOpenBudgetSettings) {
                        Text("编辑本月预算")
                    }
                }
                item(key = "account-summary") {
                    AccountSummaryCard(
                        totals = budgetTotals,
                        rows = budgetRows,
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySection(
    day: DayGroup,
    categoryById: Map<Long, Category>,
    /** 转账行用（accountId → 账户名）；普通收支行用不到 */
    accountNameById: Map<Long, String> = emptyMap(),
    onTransactionClick: (Transaction) -> Unit,
) {
    // Phase 26：日期分组头改用 SectionLabel ——
    // 以前是自己画 titleMedium 粗体，跟清单页的分组头两套长相。
    // 收支小计的颜色改走固定语义色：收入/支出不该跟着用户主题色变。
    val appColors = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
    ) {
        SectionLabel(
            title = FinanceDateUtils.formatRelativeDate(day.dayMillis),
            count = null,
            modifier = Modifier.padding(horizontal = 0.dp),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    if (day.totalExpense > 0) {
                        Text(
                            text = "支 ${MoneyFormat.format(day.totalExpense)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = appColors.expenseInk,
                        )
                    }
                    if (day.totalIncome > 0) {
                        Text(
                            text = "收 ${MoneyFormat.format(day.totalIncome)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = appColors.incomeInk,
                        )
                    }
                }
            },
        )

        day.transactions.forEach { tx ->
            TransactionRow(
                transaction = tx,
                category = categoryById[tx.categoryId],
                // Phase 5：转账的对方账户名。拿不到时 TransactionRow 自己降级成不显示自/至
                counterpartyName = tx.transferAccountId?.let { accountNameById[it] },
                onClick = { onTransactionClick(tx) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = AppSpacing.xl),
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
                modifier = Modifier.padding(top = AppSpacing.sm),
            )
        }
    }
}