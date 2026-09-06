package com.example.personalassistant.feature.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import com.example.personalassistant.feature.calendar.domain.model.CalendarViewType
import com.example.personalassistant.feature.calendar.domain.model.MonthSummary
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveDayDetailUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveMonthSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Phase 7c：日历主页 ViewModel
 *
 * ## 数据流
 * - 当前月份 [currentYearMonth] → 派生 monthStart / monthEnd / today 三个时间戳
 * - 用 [ObserveMonthSummaryUseCase] 拉该月范围的事件/任务/课程汇总
 * - 客户端把汇总转成 6×7 = 42 个 [CalendarDayCell]
 *
 * ## 操作
 * - [goToPrevMonth] / [goToNextMonth] / [goToToday]：切换显示月份
 * - [onDateClick]：选中某个日期（Sub-step 4 的 BottomSheet 用）
 * - [dismissDayDetail]：取消选中
 *
 * @OptIn(ExperimentalCoroutinesApi::class) for [flatMapLatest]
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val observeMonthSummary: ObserveMonthSummaryUseCase,
    private val observeDayDetail: ObserveDayDetailUseCase,
) : ViewModel() {

    private val _currentYearMonth = MutableStateFlow(DateUtils.currentYearMonth())
    val currentYearMonth: StateFlow<String> = _currentYearMonth.asStateFlow()

    private val _viewType = MutableStateFlow(CalendarViewType.MONTH)
    val viewType: StateFlow<CalendarViewType> = _viewType.asStateFlow()

    private val _selectedDate = MutableStateFlow<Long?>(null)
    val selectedDate: StateFlow<Long?> = _selectedDate.asStateFlow()

    private val today: Long = DateUtils.todayStartMillis()

    /**
     * 主 UI State。
     *
     * `_currentYearMonth` 变化 → flatMapLatest 重新订阅 ObserveMonthSummaryUseCase
     * 拿到新月份的 MonthSummary → 客户端转 42 个 cell。
     */
    val uiState: StateFlow<CalendarUiState> = _currentYearMonth
        .flatMapLatest { yearMonth ->
            val monthStart = DateUtils.monthStartMillis(yearMonth)
            val monthEnd = DateUtils.monthEndMillis(yearMonth)
            observeMonthSummary(monthStart, monthEnd, today)
        }
        .map { summary -> buildUiState(summary) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CalendarUiState(
                yearMonth = DateUtils.currentYearMonth(),
                monthCells = emptyList(),
                summary = emptyMap(),
                today = today,
            ),
        )

    /**
     * 选中日期的 DayDetail（Sub-step 4 用）
     */
    val selectedDayDetail: StateFlow<com.example.personalassistant.feature.calendar.domain.model.DayDetail?> =
        _selectedDate
            .flatMapLatest { date ->
                if (date == null) flowOf(null) else observeDayDetail(date)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    fun goToPrevMonth() {
        _currentYearMonth.value = shiftMonth(_currentYearMonth.value, -1)
    }

    fun goToNextMonth() {
        _currentYearMonth.value = shiftMonth(_currentYearMonth.value, 1)
    }

    fun goToToday() {
        _currentYearMonth.value = DateUtils.currentYearMonth()
    }

    fun onDateClick(date: Long) {
        _selectedDate.value = date
    }

    fun dismissDayDetail() {
        _selectedDate.value = null
    }

    fun setViewType(type: CalendarViewType) {
        _viewType.value = type
    }

    // ===== 内部 =====

    private fun buildUiState(summary: MonthSummary): CalendarUiState {
        val yearMonth = _currentYearMonth.value
        val cells = generateMonthCells(yearMonth, summary, today)
        return CalendarUiState(
            yearMonth = yearMonth,
            monthCells = cells,
            summary = summary,
            today = today,
            viewType = _viewType.value,
            selectedDate = _selectedDate.value,
        )
    }

    companion object {
        /**
         * 生成当月 6×7 = 42 个 cell（含上下月占位）
         *
         * 算法：
         * 1. 当月第一天
         * 2. 找出当月第一天是星期几（1=周一, 7=周日）
         * 3. 从「本周一」开始往前推 (dayOfWeek - 1) 天 = 网格第一格
         * 4. 一共 42 格
         */
        internal fun generateMonthCells(
            yearMonth: String,
            summary: MonthSummary,
            today: Long,
        ): List<CalendarDayCell> {
            val (year, month) = DateUtils.parseYearMonth(yearMonth)
            val zone = java.time.ZoneId.systemDefault()
            val firstDay = java.time.LocalDate.of(year, month, 1)
            // dayOfWeek 1=周一, 7=周日（ISO）
            val firstDayDow = firstDay.dayOfWeek.value
            // 网格第一格 = 当月第一天的本周一 = firstDay - (firstDayDow - 1) 天
            val gridStart = firstDay.minusDays((firstDayDow - 1).toLong())
            val cells = mutableListOf<CalendarDayCell>()
            val currentMonthValue = month
            for (i in 0 until 42) {
                val date = gridStart.plusDays(i.toLong())
                val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val daySummary = summary[dateMillis]
                cells.add(
                    CalendarDayCell(
                        date = dateMillis,
                        isCurrentMonth = date.monthValue == currentMonthValue,
                        isToday = dateMillis == today,
                        eventCount = daySummary?.eventCount ?: 0,
                        taskCount = daySummary?.taskCount ?: 0,
                        courseCount = daySummary?.courseCount ?: 0,
                    ),
                )
            }
            return cells
        }

        /**
         * 把 "yyyy-MM" 加/减 deltaMonths，返回新的 "yyyy-MM"
         *
         * 涟宝解读：原 [java.time.YearMonth] 加月后转回字符串，避免月底越界。
         */
        internal fun shiftMonth(yearMonth: String, deltaMonths: Int): String {
            val (year, month) = DateUtils.parseYearMonth(yearMonth)
            val newYm = java.time.YearMonth.of(year, month).plusMonths(deltaMonths.toLong())
            return String.format("%04d-%02d", newYm.year, newYm.monthValue)
        }
    }
}