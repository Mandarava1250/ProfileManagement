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
 * Phase 7b：月度预算 Sheet 的 ViewModel
 *
 * 7a → 7b 增量：
 * - UiState 新增 warningThresholdText 字段（用户输入用字符串，保留原始格式）
 * - loadFromPersisted 同步加载持久化的 warningThreshold
 * - save() 透传给 SetMonthlyBudgetUseCase
 * - DailyPoolCard 据此判断三档颜色（绿/橙/红）
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
                warningThresholdText = formatThreshold(budget.warningThreshold),
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

    /** 7b：用户输入警告阈值（剩余金额低于此值时变色提示） */
    fun setWarningThreshold(text: String) {
        _editing.value = _editing.value.copy(warningThresholdText = text)
    }

    fun save(onDone: () -> Unit) {
        val s = _editing.value
        val amount = s.totalAmountText.toDoubleOrNull() ?: return
        val threshold = s.warningThresholdText.toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            runCatching {
                setMonthlyBudget(
                    yearMonth = yearMonth,
                    totalAmount = amount,
                    isAutoRefresh = s.isAutoRefresh,
                    carryOverEnabled = s.carryOverEnabled,
                    warningThreshold = threshold,
                )
            }.onSuccess { onDone() }
        }
    }

    data class UiState(
        val totalAmountText: String,
        val isAutoRefresh: Boolean,
        val carryOverEnabled: Boolean,
        val warningThresholdText: String = "",
    ) {
        companion object {
            fun empty() = UiState(
                totalAmountText = "",
                isAutoRefresh = true,
                carryOverEnabled = true,
                warningThresholdText = "",
            )
        }

        val canSave: Boolean get() {
            val amount = totalAmountText.toDoubleOrNull() ?: return false
            return amount >= 0.0
        }
    }
}

/** 持久化时若 threshold = 0，输入框显示空字符串（避免显示"0.00"） */
private fun formatThreshold(value: Double): String =
    if (value <= 0.0) "" else value.toString().trimEnd('0').trimEnd('.')