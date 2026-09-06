package com.example.personalassistant.feature.finance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.MonthlyReport
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.finance.domain.usecase.GetMonthlyReportUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCategoriesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveTodayPoolUseCase
import com.example.personalassistant.feature.finance.domain.usecase.RefreshDailyPoolUseCase
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 7a：Finance 主页 ViewModel
 *
 * - 组合 5 个 Flow：dailyPool / budget / monthReport / categories / transactions
 * - 提供 refreshDailyPool() 供 UI "刷新" 按钮触发
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val observeTodayPool: ObserveTodayPoolUseCase,
    observeMonthlyBudget: ObserveMonthlyBudgetUseCase,
    observeCategories: ObserveCategoriesUseCase,
    repository: FinanceRepository,
    private val getMonthlyReport: GetMonthlyReportUseCase,
    private val refreshDailyPoolUseCase: RefreshDailyPoolUseCase,
) : ViewModel() {

    private val yearMonth = FinanceDateUtils.currentYearMonth()

    private val dailyPoolFlow = observeTodayPool()
    private val budgetFlow = observeMonthlyBudget()
    private val categoriesFlow = observeCategories.all()
    private val transactionsFlow = repository.observeAllTransactions()
    private val monthReportFlow = getMonthlyReport(yearMonth)

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

    /** 用户点"刷新"按钮（兜底：手动触发） */
    fun refreshDailyPool() {
        viewModelScope.launch {
            refreshDailyPoolUseCase()
        }
    }
}
