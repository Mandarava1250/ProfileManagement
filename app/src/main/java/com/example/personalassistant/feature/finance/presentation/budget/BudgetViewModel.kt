package com.example.personalassistant.feature.finance.presentation.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.usecase.ObserveAllBudgetsUseCase
import com.example.personalassistant.feature.finance.domain.usecase.SetMonthlyBudgetUseCase
import com.example.personalassistant.feature.finance.presentation.util.Feedback
import com.example.personalassistant.feature.finance.presentation.util.FinanceDateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * C 重构阶段 2/4/10：月度预算 Sheet 的 ViewModel
 *
 * B 修复：
 * - `save()` 解析失败时给 `Feedback.Warning`，不再 silently return
 * - `warningThreshold` 空字符串 ⇒ 传 null 给 use case（语义：未设置警告）
 * - `Feedback` 类型替代原始 String?
 *
 * D-β 内部组织：
 * - 公开方法保留旧签名（test 不破）
 * - 每个 `setX` 方法改为薄壳，内部调 `process(Intent.Xxx)`
 * - private sealed interface Intent + private fun process(intent: Intent)
 *
 * C：当前选中 [yearMonth] 是 StateFlow；[persistedBudget] 用 [observeAllBudgets] + combine
 * 过滤出该月预算（**不扩 Repository**）。
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    observeAllBudgets: ObserveAllBudgetsUseCase,
    private val setMonthlyBudget: SetMonthlyBudgetUseCase,
) : ViewModel() {

    // ============ C：当前选中的月份 ============

    private val _yearMonth = MutableStateFlow(FinanceDateUtils.currentYearMonth())
    val yearMonth: StateFlow<String> = _yearMonth.asStateFlow()

    /** 切月份查看历史预算：来源是所有 budget 里筛当前选中的那条 */
    val persistedBudget: StateFlow<MonthlyBudget?> = combine(
        observeAllBudgets(),
        _yearMonth,
    ) { all, ym -> all.find { it.yearMonth == ym } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** C：把当前选中月从全部 budgets 里抽出，供 YearMonthPickerSheet 渲染可选列表 */
    val allBudgetYearMonths: StateFlow<List<String>> = observeAllBudgets()
        .let { src ->
            kotlinx.coroutines.flow.flow {
                    emit(emptyList<String>())
                    src.collect { emit(it.map { b -> b.yearMonth }) }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ============ 草稿 / 反馈 ============

    /** 用户当前正在编辑的表单状态 */
    private val _editing = MutableStateFlow(UiState.empty())
    val editing: StateFlow<UiState> = _editing.asStateFlow()

    /** 用户最近一次操作的反馈（Success / Warning / Error） */
    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    // ============ 公开方法（旧签名 + 新增 C 方法） ============

    fun loadFromPersisted(budget: MonthlyBudget?) {
        process(Intent.LoadFromPersisted(budget))
    }

    fun setAmount(text: String) {
        process(Intent.SetAmount(text))
    }

    fun setAutoRefresh(value: Boolean) {
        process(Intent.SetAutoRefresh(value))
    }

    fun setCarryOverEnabled(value: Boolean) {
        process(Intent.SetCarryOverEnabled(value))
    }

    /** 7b：用户输入警告阈值（剩余金额低于此值时变色提示） */
    fun setWarningThreshold(text: String) {
        process(Intent.SetWarningThreshold(text))
    }

    fun save(onDone: () -> Unit) {
        process(Intent.Save(onDone))
    }

    /** C：直接跳到任意 yyyy-MM */
    fun setYearMonth(ym: String) {
        _yearMonth.value = ym
    }

    /** C：上个月（YearMonth.plusMonths(-1)），格式 yyyy-MM */
    fun goPrevMonth() {
        _yearMonth.value = YearMonth.parse(_yearMonth.value).minusMonths(1).toString()
    }

    /** C：下个月 */
    fun goNextMonth() {
        _yearMonth.value = YearMonth.parse(_yearMonth.value).plusMonths(1).toString()
    }

    // ============ 内部 reducer ============

    /**
     * D-β：所有公开方法最终都进入本入口。
     * 故意 `private`：reducer 是 VM 内部实现细节；测试通过公开 `setX` / `save` 验证行为，
     * 想直接对 reducer 跑用例就把 Intent 标 internal（暂不需要）。
     */
    private fun process(intent: Intent) {
        when (intent) {
            is Intent.LoadFromPersisted -> _editing.value = if (intent.budget != null) {
                UiState(
                    totalAmountText = intent.budget.totalAmount.toString().trimEnd('0').trimEnd('.'),
                    isAutoRefresh = intent.budget.isAutoRefresh,
                    carryOverEnabled = intent.budget.carryOverEnabled,
                    warningThresholdText = formatThreshold(intent.budget.warningThreshold),
                )
            } else {
                UiState.empty()
            }

            is Intent.SetAmount -> _editing.value = _editing.value.copy(totalAmountText = intent.text)
            is Intent.SetAutoRefresh -> _editing.value = _editing.value.copy(isAutoRefresh = intent.value)
            is Intent.SetCarryOverEnabled -> _editing.value = _editing.value.copy(carryOverEnabled = intent.value)
            is Intent.SetWarningThreshold -> _editing.value = _editing.value.copy(warningThresholdText = intent.text)

            is Intent.Save -> {
                val s = _editing.value
                val amount = s.totalAmountText.toDoubleOrNull()
                if (amount == null) {
                    // B 修复：非法金额给 Warning，不再 silently return
                    _feedback.value = Feedback.Warning("金额格式不对，请输入有效数字")
                    return
                }
                // B 修复：空字符串 / 解析失败 ⇒ null（语义：未设置警告阈值）
                val threshold = s.warningThresholdText.toDoubleOrNull()
                // C：save 透传当前选中的 yearMonth，而不是固定的当月
                val ym = _yearMonth.value
                viewModelScope.launch {
                    runCatching {
                        setMonthlyBudget(
                            yearMonth = ym,
                            totalAmount = amount,
                            isAutoRefresh = s.isAutoRefresh,
                            carryOverEnabled = s.carryOverEnabled,
                            warningThreshold = threshold,
                        )
                    }.onSuccess {
                        _feedback.value = Feedback.Success("预算已保存")
                        intent.onDone()
                    }.onFailure { e ->
                        _feedback.value = Feedback.Error(
                            message = "保存失败：${e.message ?: "未知错误"}",
                            cause = e,
                        )
                    }
                }
            }
        }
    }

    /** D-β：所有用户意图的统一入口（private sealed，VM 外不可见） */
    private sealed interface Intent {
        data class LoadFromPersisted(val budget: MonthlyBudget?) : Intent
        data class SetAmount(val text: String) : Intent
        data class SetAutoRefresh(val value: Boolean) : Intent
        data class SetCarryOverEnabled(val value: Boolean) : Intent
        data class SetWarningThreshold(val text: String) : Intent
        data class Save(val onDone: () -> Unit) : Intent
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

/**
 * 持久化时若 threshold = 0，输入框显示空字符串（避免显示"0.00"）。
 * 注意：threshold 仍是非空 Double（领域模型保持稳定），只是"未设置"在 UI / use case 层用 null 表达。
 */
private fun formatThreshold(value: Double): String =
    if (value <= 0.0) "" else value.toString().trimEnd('0').trimEnd('.')