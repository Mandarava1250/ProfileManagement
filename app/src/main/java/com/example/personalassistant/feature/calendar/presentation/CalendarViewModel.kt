package com.example.personalassistant.feature.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import com.example.personalassistant.feature.calendar.domain.model.CalendarViewType
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.EventCategory
import com.example.personalassistant.feature.calendar.domain.model.EventConflictInfo
import com.example.personalassistant.feature.calendar.domain.model.MonthSummary
import com.example.personalassistant.feature.calendar.domain.usecase.AddEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.CancelEventReminderUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DeleteEventCategoryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DeleteEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DetectEventLiveConflictsUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.DetectEventSavedConflictsUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveDayDetailUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveEventCategoriesUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveMonthSummaryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ScheduleEventReminderUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpdateEventUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpsertEventCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
 * @OptIn(FlowPreview::class) for [debounce]
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val observeMonthSummary: ObserveMonthSummaryUseCase,
    private val observeDayDetail: ObserveDayDetailUseCase,
    private val addEvent: AddEventUseCase,
    private val updateEvent: UpdateEventUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val scheduleEventReminder: ScheduleEventReminderUseCase,
    private val cancelEventReminder: CancelEventReminderUseCase,
    // Phase 8 Sub-step 9：事件分类管理
    private val observeEventCategories: ObserveEventCategoriesUseCase,
    private val upsertEventCategory: UpsertEventCategoryUseCase,
    private val deleteEventCategory: DeleteEventCategoryUseCase,
    // Phase 8 Sub-step 9 Step 4.2：事件冲突检测
    private val detectLiveConflict: DetectEventLiveConflictsUseCase,
    private val detectSavedConflict: DetectEventSavedConflictsUseCase,
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

    /**
     * Phase 8 Sub-step 9：事件分类列表（Flow）
     *
     * - 由 EventCategoryRepository.observeAll() 提供
     * - UI 用：EditEventSheet 里的 CategorySelectorRow + CategoryManagementDialog
     */
    val eventCategories: StateFlow<List<EventCategory>> = observeEventCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Phase 8 Sub-step 9：分类管理 Dialog 显示状态
     */
    private val _showCategoryManager = MutableStateFlow(false)
    val showCategoryManager: StateFlow<Boolean> = _showCategoryManager.asStateFlow()

    /**
     * Phase 8 Sub-step 9：分类管理错误信息
     */
    private val _categoryManagerError = MutableStateFlow<String?>(null)
    val categoryManagerError: StateFlow<String?> = _categoryManagerError.asStateFlow()

    /**
     * Phase 8 Sub-step 9 Step 4.2：编辑实时冲突（debounce 后）
     *
     * - null = 编辑器未打开（_editingState == null）
     * - 非 null = [detectLiveConflict] 的最新结果（含整天 / 学期外等空冲突场景）
     */
    private val _liveConflictInfo = MutableStateFlow<EventConflictInfo?>(null)
    val liveConflictInfo: StateFlow<EventConflictInfo?> = _liveConflictInfo.asStateFlow()

    /**
     * Phase 8 Sub-step 9 Step 4.2：已保存事件冲突 map
     *
     * - key = event.id
     * - value = 该事件对应的 EventConflictInfo
     * - selectedDayDetail 为 null / weekNumber 为空 → map 为空
     */
    private val _savedConflictsByEventId = MutableStateFlow<Map<Long, EventConflictInfo>>(emptyMap())
    val savedConflictsByEventId: StateFlow<Map<Long, EventConflictInfo>> = _savedConflictsByEventId.asStateFlow()

    private val today: Long = DateUtils.todayStartMillis()

    init {
        // 1. 编辑实时冲突：_editingState debounce(150ms) → detectLiveConflict
        // 关闭编辑器（state=null）时短路 emit null
        // 不用 flatMapLatest 是为了让 collect 内可以做"状态判定 → null or detect"
        // （flow { emit(...) } + flowOf(null) 的类型推断会让结果退化为 Flow<Nothing?>）
        //
        // 防御性 runCatching：草稿态 title="" 时 [EventEditState.toEvent] 会触发 Event.require()。
        // 我们先显式判断 title.isBlank() 跳过 detect（草稿态 emit null 表示"无冲突"），
        // 再用 runCatching 兜底，避免异常泄露到 viewModelScope。
        viewModelScope.launch {
            _editingState
                .debounce(150L)
                .collect { state ->
                    val info = when {
                        state == null -> null
                        state.title.isBlank() -> null
                        else -> runCatching {
                            detectLiveConflict(state.toEvent(0L, 0L))
                        }.getOrNull()
                    }
                    _liveConflictInfo.value = info
                }
        }

        // 2. 已保存冲突：selectedDayDetail 变化 → 对每个有 id 的 event 串行检测
        // weekNumber 为空时直接短路返回 emptyMap
        viewModelScope.launch {
            selectedDayDetail
                .filterNotNull()
                .map { detail -> buildSavedConflicts(detail) }
                .collect { _savedConflictsByEventId.value = it }
        }
    }

    /**
     * 主 UI State。
     *
     * `_currentYearMonth` 变化 → flatMapLatest 重新订阅 ObserveMonthSummaryUseCase
     * 拿到新月份的 MonthSummary → 客户端转 42 个 cell。
     */
    val uiState: StateFlow<CalendarUiState> = combine(
        _currentYearMonth.flatMapLatest { yearMonth ->
            val monthStart = DateUtils.monthStartMillis(yearMonth)
            val monthEnd = DateUtils.monthEndMillis(yearMonth)
            observeMonthSummary(monthStart, monthEnd, today)
        },
        _liveConflictInfo,
        _savedConflictsByEventId,
    ) { summary, live, saved ->
        buildUiState(summary, live, saved)
    }
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
     * Phase 8 Sub-step 9：分类选择变化（EditEventSheet 内的 Chip 切换）
     *
     * - [categoryId] = null → 无分类
     * - [categoryId] != null → 关联到该分类
     *
     * 只修改 [editingState] 的 categoryId，不持久化（等 saveEvent 才落库）
     */
    fun onEventCategoryChange(categoryId: Long?) {
        _editingState.value?.let { current ->
            _editingState.value = current.copy(categoryId = categoryId)
        }
    }

    /**
     * Phase 8 Sub-step 9：打开分类管理 Dialog
     */
    fun openCategoryManager() {
        _categoryManagerError.value = null
        _showCategoryManager.value = true
    }

    /**
     * Phase 8 Sub-step 9：关闭分类管理 Dialog
     */
    fun closeCategoryManager() {
        _showCategoryManager.value = false
        _categoryManagerError.value = null
    }

    /**
     * Phase 8 Sub-step 9：upsert 分类（新建/编辑）
     *
     * - 失败时写 [_categoryManagerError]，Dialog 显示
     * - 成功时 Flow 自动 emit 新列表（eventCategories 自动更新）
     */
    fun upsertCategory(category: EventCategory) {
        viewModelScope.launch {
            try {
                upsertEventCategory(category)
                _categoryManagerError.value = null
            } catch (e: IllegalArgumentException) {
                _categoryManagerError.value = e.message ?: "保存分类失败"
            }
        }
    }

    /**
     * Phase 8 Sub-step 9：删除分类
     */
    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            try {
                deleteEventCategory(id)
                _categoryManagerError.value = null
            } catch (e: IllegalArgumentException) {
                _categoryManagerError.value = e.message ?: "删除分类失败"
            }
        }
    }

    /**
     * Phase 8 Sub-step 9：清除分类管理错误状态（Dialog 关闭时调用）
     */
    fun clearCategoryManagerError() {
        _categoryManagerError.value = null
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
     * 更新场景下保留原 createdAt 会引入额外的 repo 查询，收益小，先简化。
     *
     * ## Phase 8 Sub-step 1：提醒同步
     * 事件写入成功后，根据 reminderMinutes / startTime 决定：
     * - 都有效 → schedule 一次（覆盖旧的 PendingIntent）
     * - 任意无效 → 如果是更新场景（originalId > 0），额外 cancel 一次（清残留）
     * - 新建场景下无效无需 cancel（本来就没旧提醒）
     */
    fun saveEvent(state: EventEditState) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                var event = state.toEvent(createdAt = now, updatedAt = now)
                if (state.originalId == 0L) {
                    val newId = addEvent(event)
                    event = event.copy(id = newId)
                } else {
                    updateEvent(event)
                }
                syncReminder(event, isUpdate = state.originalId > 0L)
                _editingState.value = null
                _editorError.value = null
            } catch (e: IllegalArgumentException) {
                _editorError.value = e.message ?: "保存失败"
            }
        }
    }

    /**
     * 根据 event 当前 reminder 状态与调度器同步。
     *
     * - reminder 非空 + startTime 非空 → schedule
     * - reminder 空 / startTime 空：
     *   - 更新场景：cancel 一次，清掉可能存在的旧 PendingIntent
     *   - 新建场景：什么都不做（数据库里没旧记录）
     */
    private suspend fun syncReminder(event: Event, isUpdate: Boolean) {
        val canSchedule = event.reminder != null && event.startTime != null
        if (canSchedule) {
            scheduleEventReminder(event)
        } else if (isUpdate && event.id > 0L) {
            cancelEventReminder(event.id)
        }
    }

    /**
     * 删除当前编辑器里的事件（仅 originalId > 0 的情况下有意义）。
     *
     * 新建模式下保存失败也可用于放弃草稿，但 UseCase 内部会 require id > 0。
     * 失败写 _editorError，成功关闭编辑器。
     *
     * 删除时同时取消闹钟（即使 reminder 为 null 也要 cancel，避免旧 PendingIntent 残留）。
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
                cancelEventReminder(current.originalId)
                _editingState.value = null
                _editorError.value = null
            } catch (e: IllegalArgumentException) {
                _editorError.value = e.message ?: "删除失败"
            }
        }
    }

    // ===== 内部 =====

    private fun buildUiState(
        summary: MonthSummary,
        liveConflict: EventConflictInfo?,
        savedConflicts: Map<Long, EventConflictInfo>,
    ): CalendarUiState {
        val yearMonth = _currentYearMonth.value
        val cells = generateMonthCells(yearMonth, summary, today)
        return CalendarUiState(
            yearMonth = yearMonth,
            monthCells = cells,
            summary = summary,
            today = today,
            viewType = _viewType.value,
            selectedDate = _selectedDate.value,
            liveConflictInfo = liveConflict,
            savedConflictsByEventId = savedConflicts,
        )
    }

    /**
     * Phase 8 Sub-step 9 Step 4.2：构造 selectedDayDetail 中每个 event 的冲突 map
     *
     * 规则：
     * - weekNumber == null（无学期 / 学期外）→ emptyMap
     * - 串行调用 detectSavedConflict(event)：单日 events ≤ 10，串行开销可接受
     * - event.id <= 0 跳过（理论上 DayDetail.events 都是已保存的，但防御性过滤）
     */
    private suspend fun buildSavedConflicts(detail: DayDetail): Map<Long, EventConflictInfo> {
        if (detail.weekNumber == null) return emptyMap()
        val map = mutableMapOf<Long, EventConflictInfo>()
        for (event in detail.events) {
            if (event.id > 0L) {
                map[event.id] = detectSavedConflict(event)
            }
        }
        return map
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