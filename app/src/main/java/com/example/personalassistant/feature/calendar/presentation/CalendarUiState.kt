package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.feature.calendar.domain.model.CalendarDayCell
import com.example.personalassistant.feature.calendar.domain.model.CalendarViewType
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.EventConflictInfo
import com.example.personalassistant.feature.calendar.domain.model.MonthSummary

/**
 * Phase 7c：日历主页 UI 状态（聚合输出）
 *
 * 由 [CalendarViewModel] 暴露给 Composable，
 * 月视图根据此状态渲染 6×7 网格 + 顶部导航 + 底部 BottomSheet 触发条件。
 *
 * @param yearMonth 当前显示月份（"yyyy-MM"）
 * @param monthCells 6×7 = 42 个 cell（含上下月占位）
 * @param summary 月范围每日汇总（用于 badge）
 * @param today 今日 00:00 时间戳（用于高亮）
 * @param viewType 当前视图类型（月/周/日，Sub-step 4 用）
 * @param selectedDate 用户当前选中的日期（用于触发 DayDetailSheet，Sub-step 4 用）
 * @param selectedDayDetail 选中日期的详情（null = 未选中或数据未到）
 * @param liveConflictInfo Phase 8 Sub-step 9 Step 4.2：编辑实时冲突（debounce 后）。
 *        null = 编辑器未打开 或 整天事件 / 无学期 / 学期外。
 *        非空且 conflictingCourses 非空 → EditEventSheet 显示红色横幅。
 * @param savedConflictsByEventId Phase 8 Sub-step 9 Step 4.2：已保存事件冲突 map。
 *        key = event.id，value = 该事件对应的 EventConflictInfo。
 *        用于 DayDetailSheet 行末的 ConflictBadge。
 */
data class CalendarUiState(
    val yearMonth: String,
    val monthCells: List<CalendarDayCell>,
    val summary: MonthSummary,
    val today: Long,
    val viewType: CalendarViewType = CalendarViewType.MONTH,
    val selectedDate: Long? = null,
    val selectedDayDetail: DayDetail? = null,
    val liveConflictInfo: EventConflictInfo? = null,
    val savedConflictsByEventId: Map<Long, EventConflictInfo> = emptyMap(),
    /**
     * Phase 8 Sub-step 9 Step 5：月视图农历角标
     *
     * key = [CalendarDayCell.date]（当天 00:00 时间戳），value = 该格的农历展示信息。
     * 上下月占位格也包含（让月视图跨月过渡自然）。
     *
     * 生成端在 [CalendarViewModel] 里通过 [com.example.personalassistant.core.lunar.LunarCalendar] 计算。
     */
    val monthLunarLabels: Map<Long, LunarCellLabel> = emptyMap(),
) {

    /**
     * Phase 8 Sub-step 9 Step 5：单个 cell 的农历角标内容。
     *
     * @param text UI 要显示的字串（节日名 > 月份 > 日）
     * @param isFestival 是否节日（农历节日或公历节日），决定用 tertiary 高亮 vs onSurfaceVariant 弱化
     */
    data class LunarCellLabel(
        val text: String,
        val isFestival: Boolean,
    )
    /**
     * 月份展示标题（如 "2024 年 12 月"）
     */
    val yearMonthDisplay: String
        get() {
            val parts = yearMonth.split("-")
            return if (parts.size == 2) "${parts[0]} 年 ${parts[1].toInt()} 月" else yearMonth
        }

    /**
     * 是否今天在当前显示的月份内（用于决定 "回到今天" 按钮是否高亮）
     */
    val isShowingCurrentMonth: Boolean
        get() {
            // today 是 Long 类型，需转成 yyyy-MM 比较
            val (y, m, _) = timestampToYmd(today)
            val ym = yearMonth
            return ym == "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}"
        }

    private fun timestampToYmd(ts: Long): Triple<Int, Int, Int> {
        val instant = java.time.Instant.ofEpochMilli(ts)
        val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return Triple(date.year, date.monthValue, date.dayOfMonth)
    }
}