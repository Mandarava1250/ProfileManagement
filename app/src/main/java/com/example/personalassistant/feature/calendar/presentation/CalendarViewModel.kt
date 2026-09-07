package com.example.personalassistant.feature.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import com.example.personalassistant.feature.calendar.domain.model.CalendarViewType
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.MonthSummary
import com.example.personalassistant.feature.calendar.domain.usecase.AddEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DeleteEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveDayDetailUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveMonthSummaryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpdateEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val addEvent: AddEventUseCase,
    private val updateEvent: UpdateEventUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
) : ViewModel() {

    private val _currentYearMonth = MutableStateFlow(DateUtils.currentYearMonth())
    val currentYearMonth: StateFlow<String> = _currentYearMonth.asStateFlow()

    private val _viewType = MutableStateFlow(CalendarViewType.MONTH)
    val viewType: StateFlow<CalendarViewType> = _viewType.asStateFlow()

    private val _selectedDate = MutableStateFlow<Long?>(null)
    val selectedDate: StateFlow<Long?> = _selectedDate.asStateFlow()

    /**
     * Sub-step 5：编辑状态（null = 关闭 EditEventSheet）
     */
    private val _editingState = MutableStateFlow<EventEditState?>(null)
    val editingState: StateFlow<EventEditState?> = _editingState.asStateFlow()

    /**
     * Sub-step 5：保存/删除事件时的瞬时错误（Snackbar 用）
     */
    private val _editorError = MutableStateFlow<String?>(null)
    val editorError: StateFlow<String?> = _editorError.asStateFlow()

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

    /**
     * 周视图 7 个 cell（基于当前选中日期所在的周）
     *
     * - 选中日期变化 → flatMapLatest 重新订阅 ObserveMonthSummaryUseCase(weekStart, weekEnd+1, today)
     * - 输出 List<CalendarDayCell> 共 7 个（周一~周日），用于周视图组件
     *
     * Sub-step 4：周视图组件（WeekCalendarView）订阅这个流
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weekCells: StateFlow<List<CalendarDayCell>> = _selectedDate
        .filterNotNull()
        .flatMapLatest { date ->
            val (weekStart, weekEndExclusive) = computeWeekRange(date)
            observeMonthSummary(weekStart, weekEndExclusive, today)
                .map { summary -> generateWeekCells(date, summary, today) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
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
        // 切换到周/日视图时，把 selectedDate 设为今天（首次进入视图的位置）
        if (_selectedDate.value == null) {
            _selectedDate.value = today
        }
    }

    /**
     * Sub-step 4：通用「上一个」按钮
     * - MONTH：上个月
     * - WEEK：上一周
     * - DAY：上一天
     */
    fun goToPrevious() {
        when (_viewType.value) {
            CalendarViewType.MONTH -> _currentYearMonth.value = shiftMonth(_currentYearMonth.value, -1)
            CalendarViewType.WEEK -> _selectedDate.value = _selectedDate.value?.let { shiftDay(it, -7) } ?: today
            CalendarViewType.DAY -> _selectedDate.value = _selectedDate.value?.let { shiftDay(it, -1) } ?: today
        }
    }

    /**
     * Sub-step 4：通用「下一个」按钮
     * - MONTH：下个月
     * - WEEK：下一周
     * - DAY：下一天
     */
    fun goToNext() {
        when (_viewType.value) {
            CalendarViewType.MONTH -> _currentYearMonth.value = shiftMonth(_currentYearMonth.value, 1)
            CalendarViewType.WEEK -> _selectedDate.value = _selectedDate.value?.let { shiftDay(it, 7) } ?: today
            CalendarViewType.DAY -> _selectedDate.value = _selectedDate.value?.let { shiftDay(it, 1) } ?: today
        }
    }

    /**
     * Sub-step 4：通用「回到今天」按钮
     * - MONTH：跳回当前月
     * - WEEK/DAY：把选中日期设为今天
     */
    fun goToCurrent() {
        _currentYearMonth.value = DateUtils.currentYearMonth()
        _selectedDate.value = today
    }

    // ===== Sub-step 5：事件编辑动作 =====

    /**
     * 启动「新建事件」编辑器，初始日期 = [date]（当天 00:00 时间戳）。
     *
     * - date = 0L 时使用 selectedDate，再没有就回退到今天
     * - 默认时长 9:00-10:00（避免「未填时段」歧义）
     */
    fun startAddEvent(date: Long = 0L) {
        val resolvedDate = when {
            date > 0L -> date
            _selectedDate.value != null -> _selectedDate.value!!
            else -> today
        }
        _editingState.value = EventEditState.newOnDate(resolvedDate)
    }

    /**
     * 启动「编辑已有事件」编辑器。
     */
    fun startEditEvent(event: Event) {
        _editingState.value = EventEditState.fromEvent(event)
    }

    /**
     * 关闭编辑器（取消/保存/删除后调用）。
     */
    fun clearEditor() {
        _editingState.value = null
        _editorError.value = null
    }

    /**
     * 直接修改编辑状态（表单字段变更用）。
     */
    fun updateEditingState(state: EventEditState) {
        _editingState.value = state
    }

    /**
     * 清除错误状态（Snackbar 显示后调用）。
     */
    fun clearError() {
        _editorError.value = null
    }

    /**
     * 保存事件（新建 or 更新）
     *
     * - originalId == 0 → 走 AddEventUseCase
     * - originalId > 0 → 走 UpdateEventUseCase
     *
     * 失败时（require 抛 IllegalArgumentException）写 _editorError，成功则清空编辑器。
     *
     * 设计取舍：createdAt / updatedAt 都用 System.currentTimeMillis()。
     * 更新场景下"保留原 createdAt"会引入额外的 repo 查询，收益小，先简化。
     */
    fun saveEvent(state: EventEditState) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val event = state.toEvent(createdAt = now, updatedAt = now)
                if (state.originalId == 0L) {
                    addEvent(event)
                } else {
                    updateEvent(event)
                }
                _editingState.value = null
                _editorError.value = null
            } catch (e: IllegalArgumentException) {
                _editorError.value = e.message ?: "保存失败"
            }
        }
    }

    /**
     * 删除当前编辑器里的事件（仅 originalId > 0 的情况下有意义）。
     *
     * 新建模式下保存失败也可用于「放弃草稿」，但 UseCase 内部会 require id > 0。
     * 失败写 _editorError，成功关闭编辑器。
     */
    fun deleteEventFromEditor() {
        val current = _editingState.value ?: return
        if (current.originalId == 0L) {
            // 新建草稿 → 直接关闭编辑器
            _editingState.value = null
            return
        }
        viewModelScope.launch {
            try {
                deleteEventUseCase(current.originalId)
                _editingState.value = null
                _editorError.value = null
            } catch (e: IllegalArgumentException) {
                _editorError.value = e.message ?: "删除失败"
            }
        }
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

        /**
         * Sub-step 4：把指定日期加/减 deltaDays 天，返回新的当天 00:00 时间戳。
         *
         * 用于周/日视图的「上/下一周」「上/下一天」导航。
         */
        internal fun shiftDay(date: Long, deltaDays: Int): Long {
            val zone = java.time.ZoneId.systemDefault()
            val localDate = java.time.Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
            return localDate.plusDays(deltaDays.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        }

        /**
         * Sub-step 4：计算 date 所在周的 [weekStart, weekEndExclusive)
         * - weekStart：本周一 00:00 时间戳
         * - weekEndExclusive：下周一 00:00 时间戳（不包含）
         *
         * 周起始日遵循 [DateUtils.WEEK_START_DAY_OF_WEEK] = 周一
         */
        internal fun computeWeekRange(date: Long): Pair<Long, Long> {
            val zone = java.time.ZoneId.systemDefault()
            val localDate = java.time.Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
            // dayOfWeek: 1=周一, 7=周日（ISO）
            val dow = localDate.dayOfWeek.value
            val weekStart = localDate.minusDays((dow - 1).toLong())
                .atStartOfDay(zone).toInstant().toEpochMilli()
            val weekEndExclusive = localDate.plusDays((7 - dow).toLong())
                .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return weekStart to weekEndExclusive
        }

        /**
         * Sub-step 4：根据 [anchorDate] 所在周生成 7 个 [CalendarDayCell]
         *
         * - 顺序：周一 → 周日
         * - isCurrentMonth：周视图不分上下月，全设为 true（避免 UI 误判）
         * - 取 [summary] 里的当日计数作为徽章数据
         */
        internal fun generateWeekCells(
            anchorDate: Long,
            summary: MonthSummary,
            today: Long,
        ): List<CalendarDayCell> {
            val (weekStart, _) = computeWeekRange(anchorDate)
            val zone = java.time.ZoneId.systemDefault()
            val start = java.time.Instant.ofEpochMilli(weekStart).atZone(zone).toLocalDate()
            return (0 until 7).map { i ->
                val date = start.plusDays(i.toLong())
                val dateMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val daySummary = summary[dateMillis]
                CalendarDayCell(
                    date = dateMillis,
                    isCurrentMonth = true,
                    isToday = dateMillis == today,
                    eventCount = daySummary?.eventCount ?: 0,
                    taskCount = daySummary?.taskCount ?: 0,
                    courseCount = daySummary?.courseCount ?: 0,
                )
            }
        }
    }
}