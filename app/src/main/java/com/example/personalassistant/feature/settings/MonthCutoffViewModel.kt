package com.example.personalassistant.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Phase 13：月截止日设置 ViewModel
 *
 * ## 为什么不复用 [com.example.personalassistant.feature.finance.presentation.budget.BudgetViewModel]
 *
 * 预算 VM 的 `save()` 是「写截止日 + 写月预算」一起做的。
 * 截止日搬到设置页后，用户在设置里改个截止日却顺带把月预算也写了 —— 语义不对，
 * 而且月预算还得先填个合法金额才能保存，会把截止日这条路堵死。
 *
 * 所以这里单独开一个 VM：**只管截止日**。
 *
 * ## 写入规则（与原实现保持一致）
 *
 * - 值没变就不写：写了会连带重算「生效 ym」，把冻结点往前推
 *   （更早的账期就不该再变了）
 * - 「生效 ym」取**当前账期**：用户改设置时想的是「从现在起按新规则切」
 */
@HiltViewModel
class MonthCutoffViewModel @Inject constructor(
    private val settingsRepository: FinanceCutoffSettings,
) : ViewModel() {

    /**
     * 已保存的截止日策略（截止日 + 生效 ym）
     *
     * 单独一条流，不塞进草稿 —— 截止日是**全局设置**（所有月份、所有账户共用），
     * 不是某个月预算的属性。
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

    /** 输入框草稿（空串 = 未改动） */
    private val _cutoffDayText = MutableStateFlow("")
    val cutoffDayText: StateFlow<String> = _cutoffDayText.asStateFlow()

    /** 操作反馈（成功 / 提示），null = 无 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setCutoffDayText(text: String) {
        _cutoffDayText.value = text
    }

    fun dismissMessage() {
        _message.value = null
    }

    /**
     * 当前所在的账期 ym 标签。
     *
     * 用它做两件事：算「生效 ym」的起点、预览账期区间。
     */
    val currentCycleYm: String
        get() = BillingCycle.yearMonthOf(
            LocalDate.now(ZoneId.systemDefault()),
            cutoffPolicy.value,
        )

    /**
     * 草稿里的截止日（合法时），非法返回 null。
     *
     * UI 用它预览"保存后长什么样"。
     */
    fun draftCutoffDay(): Int? {
        val day = _cutoffDayText.value.trim().toIntOrNull() ?: return null
        if (day < BillingCycle.MIN_CUTOFF_DAY || day > BillingCycle.MAX_CUTOFF_DAY) return null
        return day
    }

    /** 保存截止日 */
    fun save() {
        val text = _cutoffDayText.value.trim()
        val day = text.toIntOrNull()
        if (day == null ||
            day < BillingCycle.MIN_CUTOFF_DAY ||
            day > BillingCycle.MAX_CUTOFF_DAY
        ) {
            _message.value =
                "月截止日要在 ${BillingCycle.MIN_CUTOFF_DAY}-${BillingCycle.MAX_CUTOFF_DAY} 之间"
            return
        }
        viewModelScope.launch {
            runCatching {
                // 当前已保存的截止日 —— 直接读设置，不走 [cutoffPolicy].value：
                // 那个 StateFlow 是 WhileSubscribed，没人订阅时 .value 永远是初值 1，
                // 会把"没改截止日"误判成"改了"，从而重写生效 ym、把冻结点往前推。
                val savedCutoffDay = settingsRepository.financeMonthCutoffDay.first()
                if (day != savedCutoffDay) {
                    val currentCycleYm = currentCycleYm
                    settingsRepository.setFinanceMonthCutoff(
                        day = day,
                        // 从**当前账期**开始生效：更早的账期区间保持不变
                        effectiveFromYm = MonthCutoffPolicy.effectiveFromFor(currentCycleYm),
                    )
                    _cutoffDayText.value = ""
                    _message.value = "月截止日已设为每月 $day 号"
                } else {
                    _message.value = "月截止日没有变化"
                }
            }.onFailure { e ->
                _message.value = "保存失败：${e.message ?: "未知错误"}"
            }
        }
    }
}
