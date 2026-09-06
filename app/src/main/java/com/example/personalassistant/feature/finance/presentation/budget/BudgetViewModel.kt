package com.example.personalassistant.feature.finance.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.usecase.ObserveMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.domain.usecase.SetMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 7a：月度预算 Sheet 的 ViewModel
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    observeMonthlyBudget: ObserveMonthlyBudgetUseCase,
    private val setMonthlyBudget: SetMonthlyBudgetUseCase,
) : ViewModel() {

    private val yearMonth = FinanceDateUtils.currentYearMonth()

    /** 持久化的当月预算 */
    val persistedBudget: StateFlow<MonthlyBudget?> = observeMonthlyBudget()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** 用户当前正在编辑的表单状态 */
    private val _editing = MutableStateFlow(UiState.empty())
    val editing: StateFlow<UiState> = _editing.asStateFlow()

    fun loadFromPersisted(budget: MonthlyBudget?) {
        _editing.value = if (budget != null) {
            UiState(
                totalAmountText = budget.totalAmount.toString().trimEnd('0').trimEnd('.'),
                isAutoRefresh = budget.isAutoRefresh,
                carryOverEnabled = budget.carryOverEnabled,
            )
        } else {
            UiState.empty()
        }
    }

    fun setAmount(text: String) {
        _editing.value = _editing.value.copy(totalAmountText = text)
    }

    fun setAutoRefresh(value: Boolean) {
        _editing.value = _editing.value.copy(isAutoRefresh = value)
    }

    fun setCarryOverEnabled(value: Boolean) {
        _editing.value = _editing.value.copy(carryOverEnabled = value)
    }

    fun save(onDone: () -> Unit) {
        val s = _editing.value
        val amount = s.totalAmountText.toDoubleOrNull() ?: return
        viewModelScope.launch {
            runCatching {
                setMonthlyBudget(
                    yearMonth = yearMonth,
                    totalAmount = amount,
                    isAutoRefresh = s.isAutoRefresh,
                    carryOverEnabled = s.carryOverEnabled,
                )
            }.onSuccess { onDone() }
        }
    }

    data class UiState(
        val totalAmountText: String,
        val isAutoRefresh: Boolean,
        val carryOverEnabled: Boolean,
    ) {
        companion object {
            fun empty() = UiState(
                totalAmountText = "",
                isAutoRefresh = true,
                carryOverEnabled = true,
            )
        }

        val canSave: Boolean get() {
            val amount = totalAmountText.toDoubleOrNull() ?: return false
            return amount >= 0.0
        }
    }
}
