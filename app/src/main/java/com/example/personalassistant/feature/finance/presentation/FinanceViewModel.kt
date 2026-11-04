package com.example.personalassistant.feature.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.CoinPool
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.MonthlyReport
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingMode
import com.example.personalassistant.feature.finance.domain.model.TodayRemainingResult
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.AccountBalance
import com.example.personalassistant.feature.finance.domain.model.AccountBudgetSummary
import com.example.personalassistant.feature.finance.domain.model.BudgetSummaryTotals
import com.example.personalassistant.feature.finance.domain.repository.FinanceDisplayRepository
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.finance.domain.repository.TransferNotAllowedException
import com.example.personalassistant.feature.finance.domain.usecase.AdjustCoinPoolUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ExportTransactionsUseCase
import com.example.personalassistant.feature.finance.domain.usecase.GetMonthlyReportUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ImportTransactionsUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveAccountBalancesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCategoriesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCoinPoolUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveTodayPoolUseCase
import com.example.personalassistant.feature.finance.domain.usecase.RefreshDailyPoolUseCase
import com.example.personalassistant.feature.finance.domain.usecase.SearchTransactionsUseCase
import com.example.personalassistant.feature.finance.domain.usecase.TransferUseCase
import com.example.personalassistant.feature.finance.domain.util.TodayRemainingCalculator
import com.example.personalassistant.feature.profile.data.CurrentAccountRepository
import com.example.personalassistant.feature.profile.data.local.AccountIds
import com.example.personalassistant.feature.settings.FinanceCutoffSettings
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import com.example.personalassistant.feature.finance.presentation.util.MoneyFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Phase 7b：Finance 主页 ViewModel
 *
 * 7a → 7b 增量：
 * - 新增 searchKeyword StateFlow + 300ms debounce 的 searchResults Flow
 * - 6 个 Flow combine：dailyPool / budget / categories / monthReport / transactions（全部 OR 搜索结果）
 * - 关键字为空时 transactions = 全量；非空时 = SearchTransactionsUseCase 输出
 * - 透传 FinanceUiState.searchKeyword 给 UI 渲染 SearchBar
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val observeTodayPool: ObserveTodayPoolUseCase,
    private val observeMonthlyBudget: ObserveMonthlyBudgetUseCase,
    observeCategories: ObserveCategoriesUseCase,
    repository: FinanceRepository,
    private val getMonthlyReport: GetMonthlyReportUseCase,
    private val searchTransactions: SearchTransactionsUseCase,
    private val exportTransactionsUseCase: ExportTransactionsUseCase,
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val refreshDailyPoolUseCase: RefreshDailyPoolUseCase,
    // ===== 批次 C =====
    private val financeDisplayRepository: FinanceDisplayRepository,
    private val adjustCoinPool: AdjustCoinPoolUseCase,
    private val observeCoinPool: ObserveCoinPoolUseCase,
    // ===== Phase 5 =====
    observeAccountBalances: ObserveAccountBalancesUseCase,
    private val transfer: TransferUseCase,
    private val currentAccountRepository: CurrentAccountRepository,
    private val settingsRepository: FinanceCutoffSettings,
) : ViewModel() {

    /** 7b：导入导出反馈（Snackbar 显示） */
    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    private val yearMonth = FinanceDateUtils.currentYearMonth()

    /** 用户搜索关键字（UI 直接 bind） */
    private val _searchKeyword = MutableStateFlow("")
    val searchKeyword: StateFlow<String> = _searchKeyword.asStateFlow()

    /** debounce 后的搜索关键字（用于触发查询，避免每帧查库） */
    private val debouncedKeyword = _searchKeyword
        .debounce(300)
        .distinctUntilChanged()

    /** 搜索结果：关键字空 → observeAllTransactions；非空 → SearchTransactionsUseCase */
    private val transactionsFlow = debouncedKeyword
        .flatMapLatest { keyword ->
            if (keyword.isBlank()) repository.observeAllTransactions()
            else searchTransactions(keyword)
        }

    private val dailyPoolFlow = observeTodayPool()
    private val budgetFlow = observeMonthlyBudget()
    private val categoriesFlow = observeCategories.all()
    private val monthReportFlow = getMonthlyReport(yearMonth)

    // ===== Phase 5：跨账户汇总与余额 =====
    // 故意不挤进下面的 uiState：那个 combine 已经占满 5 个 flow 上限（Kotlin 泛型重载只到 5），
    // 把汇总弄进去要么改造成 list+mapAt 写法（会掉类型安全），要么拆碎现有状态。
    // 汇总只在「跨账户总览」卡片里用，单独一条 StateFlow 成本最低。

    /** 全账户当月预算行（逐账户），按账户 id 升序 */
    val accountBudgetSummaries: StateFlow<List<AccountBudgetSummary>> =
        repository.observeAccountBudgetSummaries().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * 记账账期的月截止日策略（全局设置）
     *
     * 同样不挤进 uiState（理由见上：combine 已占满 5 个 flow 上限）。
     * 只用于给月报卡片算账期区间副标题，不参与统计口径（口径在 UseCase / Repository 层）。
     */
    val cutoffPolicy: StateFlow<MonthCutoffPolicy> = combine(
        settingsRepository.financeMonthCutoffDay,
        settingsRepository.financeMonthCutoffEffectiveFrom,
    ) { day, effectiveFrom ->
        MonthCutoffPolicy(cutoffDay = day, effectiveFromYm = effectiveFrom)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MonthCutoffPolicy(),
    )

    /** 跨账户合计（由 [accountBudgetSummaries] 折叠而来，不另开一条查库流） */
    val budgetTotals: StateFlow<BudgetSummaryTotals> = accountBudgetSummaries
        .map(BudgetSummaryTotals::from)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BudgetSummaryTotals.from(emptyList()),
        )

    /** 全账户余额（含期初，拼装在 ObserveAccountBalancesUseCase） */
    val accountBalances: StateFlow<List<AccountBalance>> =
        observeAccountBalances().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * 当前账户 id（转账面板靠它把转出方从「目标账户」列表里排掉）。
     *
     * 用 [SharingStarted.Eagerly] 而不是 WhileSubscribed：它是个本地默认值永远能立刻给值，
     * 而 WhileSubscribed 下面板刚展开的那一帧会是空的，用户会瞥到「当前账户自己」出现在选项里。
     */
    val currentAccountId: StateFlow<Long> = currentAccountRepository.currentAccountIdFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AccountIds.DEFAULT_ACCOUNT_ID,
        )

    /**
     * Phase 5：发起一笔转账。
     *
     * 金额解析失败在本地就拦（不进 Repository）：那属于「输入格式问题」，
     * 与 Repository 负责的「业务规则不允许」是两类错误，混在一起文案会很含。[TransferNotAllowedException]
     * 则把原因直接当提示文案显示（余额不足 / 目标不存在 / 缺转账分类等）。
     *
     * 反馈走现有的 [feedback] Snackbar，不另建一条提示通道。
     */
    fun transferTo(toAccountId: Long, amountText: String, note: String, onDone: () -> Unit) {
        val amount = amountText.trim().toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _feedback.value = "请输入大于 0 的金额"
            return
        }
        viewModelScope.launch {
            runCatching {
                transfer(
                    toAccountId = toAccountId,
                    amount = amount,
                    note = note,
                    date = System.currentTimeMillis(),
                )
            }.onSuccess {
                _feedback.value = "已转出 ${MoneyFormat.format(amount)}"
                onDone()
            }.onFailure { throwable ->
                // 业务拒绝直接显原因；其它异常加前缀，避免把堆栈级信息当成用户错误
                _feedback.value = if (throwable is TransferNotAllowedException) {
                    throwable.message ?: "转账未执行"
                } else {
                    "转账失败：${throwable.message ?: "未知错误"}"
                }
            }
        }
    }

    /**
     * 注：Kotlin Flow.combine 最多支持 5 个 flow，searchKeyword 单独暴露。
     * UI 层同时 collectAsStateWithLifecycle(这两个。
     */
    val uiState: StateFlow<FinanceUiState> = combine(
        dailyPoolFlow,
        budgetFlow,
        categoriesFlow,
        transactionsFlow,
        monthReportFlow,
    ) { pool, budget, categories, transactions, report ->
        FinanceUiState(
            isLoading = false,
            dailyPool = pool,
            monthBudget = budget,
            monthExpense = report?.totalExpense,
            categories = categories,
            transactions = transactions,
            monthReport = report,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FinanceUiState(isLoading = true),
    )

    fun setSearchKeyword(keyword: String) {
        _searchKeyword.value = keyword
    }

    // ==================== 批次 C：「今日剩余」口径与派生值 ====================

    /**
     * 「今日剩余」的显示口径（FIXED / AUTO）
     *
     * 走**独立** StateFlow 而不是塞进 `uiState`：后者已占满 5 流上限（见上）。
     */
    val todayRemainingMode: StateFlow<TodayRemainingMode> =
        financeDisplayRepository.observeTodayRemainingMode()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TodayRemainingMode.DEFAULT,
            )

    /**
     * 今日剩余（**纯计算**，不落库）
     *
     * - FIXED → 本月可用余额 − 本月已支出
     * - AUTO  → 上者 ÷ 账期剩余天数（含今天）
     *
     * null = 未设预算（UI 显示「未设置预算」）。
     *
     * ⚠️ 这里刻意**不**读 `daily_pools.remainingAmount`：
     * 那是「当天发下来的日额度」的快照，改预算后它不会立刻变
     * （那正是批次 C 要修的 bug），而派生的「今日剩余」必须跟着预算实时走。
     *
     * 跨零点会自动重算：`uiState` 依赖的 `dailyPoolFlow` 在零点会发新值
     * （见 `FinanceRepositoryImpl.observeTodayPool` 的跨零点处理），
     * 于是这条 combine 会被重新触发，`LocalDate.now()` 也就跟着刷新了。
     */
    val todayRemainingResult: StateFlow<TodayRemainingResult> = combine(
        todayRemainingMode,
        uiState,
        cutoffPolicy,
    ) { mode, state, policy ->
        val days = BillingCycle.remainingDays(LocalDate.now(), policy)
        val total = state.monthBudget?.totalAmount
        val expense = state.monthExpense
        TodayRemainingResult(
            mode = mode,
            amount = TodayRemainingCalculator.compute(mode, total, expense, days),
            monthRemaining = TodayRemainingCalculator.monthRemaining(total, expense),
            remainingDays = days,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayRemainingResult(),
    )

    /**
     * 零钱池余额（每个账户一行；null = 该账户还没建过零钱池）
     *
     * 独立 StateFlow 的理由同 `todayRemainingResult`（`uiState` 已满 5 流）。
     */
    val coinPool: StateFlow<CoinPool?> =
        observeCoinPool()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    /** 切换「今日剩余」的显示口径 */
    fun setTodayRemainingMode(mode: TodayRemainingMode) {
        viewModelScope.launch {
            runCatching {
                financeDisplayRepository.setTodayRemainingMode(mode)
            }.onFailure {
                _feedback.value = "切换口径失败：${it.message ?: "未知错误"}"
            }
        }
    }

    /** 存 / 取零钱池（[delta] 正 = 存，负 = 取；余额允许为负） */
    fun adjustCoinPool(delta: Double, note: String?) {
        viewModelScope.launch {
            runCatching {
                adjustCoinPool(delta = delta, note = note)
            }.onSuccess {
                _feedback.value = if (delta >= 0) "已存入零钱池" else "已从零钱池取出"
            }.onFailure {
                _feedback.value = "零钱池操作失败：${it.message ?: "未知错误"}"
            }
        }
    }

    /** 用户点"刷新"按钮（兜底：手动触发） */
    fun refreshDailyPool() {
        viewModelScope.launch {
            refreshDailyPoolUseCase.force()
        }
    }

    /**
     * 7b：导出全部流水为 CSV。
     * - 返回 CSV 字符串
     * - 设置 feedback Snackbar（"已导出 N 行，N 字节"）
     *
     * 实际写入文件交给 UI 层（FinanceScreen）处理（7c 接 ActivityResult Contracts）。
     */
    fun exportTransactions() {
        viewModelScope.launch {
            runCatching {
                val csv = exportTransactionsUseCase()
                val rowCount = csv.lines().size - 1 // minus header
                csv
            }.onSuccess { csv ->
                val rowCount = csv.lines().size - 1
                _feedback.value = "已导出 $rowCount 行（${csv.length} 字节）"
            }.onFailure {
                _feedback.value = "导出失败：${it.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 7b：从 CSV 文本批量导入。
     * 入口：UI 层把用户输入 / 选中的 CSV 内容喂进来。
     */
    fun importTransactions(csvText: String) {
        viewModelScope.launch {
            runCatching {
                importTransactionsUseCase(csvText)
            }.onSuccess { summary ->
                val msg = buildString {
                    append("导入成功 ${summary.imported} 条")
                    if (summary.skipped.isNotEmpty()) {
                        append("，跳过 ${summary.skipped.size} 条")
                    }
                }
                _feedback.value = msg
            }.onFailure {
                _feedback.value = "导入失败：${it.message ?: "未知错误"}"
            }
        }
    }
}