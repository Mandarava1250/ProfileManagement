package com.example.personalassistant.feature.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.MonthlyReport
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.finance.domain.usecase.ExportTransactionsUseCase
import com.example.personalassistant.feature.finance.domain.usecase.GetMonthlyReportUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ImportTransactionsUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCategoriesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveTodayPoolUseCase
import com.example.personalassistant.feature.finance.domain.usecase.RefreshDailyPoolUseCase
import com.example.personalassistant.feature.finance.domain.usecase.SearchTransactionsUseCase
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    observeMonthlyBudget: ObserveMonthlyBudgetUseCase,
    observeCategories: ObserveCategoriesUseCase,
    repository: FinanceRepository,
    private val getMonthlyReport: GetMonthlyReportUseCase,
    private val searchTransactions: SearchTransactionsUseCase,
    private val exportTransactionsUseCase: ExportTransactionsUseCase,
    private val importTransactionsUseCase: ImportTransactionsUseCase,
    private val refreshDailyPoolUseCase: RefreshDailyPoolUseCase,
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

    /** 用户点"刷新"按钮（兜底：手动触发） */
    fun refreshDailyPool() {
        viewModelScope.launch {
            refreshDailyPoolUseCase()
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