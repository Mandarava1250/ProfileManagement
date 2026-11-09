package com.example.personalassistant.feature.calendar.domain.repository

import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.MonthSummary
import kotlinx.coroutines.flow.Flow

/**
 * Phase 7c：calendar 模块领域仓库接口
 *
 * 由 Data 层的 [com.example.personalassistant.feature.calendar.data.repository.CalendarRepositoryImpl] 实现。
 *
 * ## 接口语义
 * - **读流**：observeXxx 返回 Flow，UI 层直接 collect
 * - **写操作**：suspend，由 UseCase 层调用
 *
 * ## 范围说明
 * - 本接口**只管理 Event 实体**（CRUD + 查询）
 * - 与 Task / Course 的"联动"在联动层（Sub-step 2 的 ObserveDayDetailUseCase）
 *   直接注入各自的 DAO 实现，不污染本接口
 *
 * ## 时间戳约定
 * - [date] / 单日查询参数：当天 00:00 时间戳（与 EventEntity.date 一致）
 * - 月范围参数：[monthStart, monthEnd) 半开区间
 */
interface CalendarRepository {

    // ===== 单日 / 区间查询 =====

    /**
     * 观察单日事件（按 isAllDay DESC, startTime ASC 排序）。
     *
     * @param date 当天 00:00 时间戳
     */
    fun observeEventsByDate(date: Long): Flow<List<Event>>

    /**
     * 观察月范围事件（按 date ASC, startTime ASC）。
     *
     * @param monthStart 当月第一天 00:00 时间戳（包含）
     * @param monthEnd 下月第一天 00:00 时间戳（不包含）
     */
    fun observeEventsByMonthRange(monthStart: Long, monthEnd: Long): Flow<List<Event>>

    /**
     * 观察未来 N 天事件（首页"接下来"列表用）。
     *
     * @param today 今天 00:00 时间戳（包含）
     * @param futureEnd today + N 天（不包含）
     */
    fun observeUpcoming(today: Long, futureEnd: Long): Flow<List<Event>>

    /**
     * 按 id 观察（编辑页用，响应式）。
     */
    fun observeEventById(id: Long): Flow<Event?>

    /**
     * 按 id 取事件（一次性查询）。
     */
    suspend fun getEventById(id: Long): Event?

    // ===== 增删改 =====

    /**
     * 新增事件。
     *
     * - id = 0 自动生成；id > 0 触发 REPLACE（一般新建时传 0）
     * - 返回落库后的 id
     */
    suspend fun addEvent(event: Event): Long

    /**
     * 更新事件。
     *
     * - id 必须 > 0（更新已存在的事件）
     * - id 不存在时静默 no-op
     */
    suspend fun updateEvent(event: Event)

    /**
     * 删除事件。
     *
     * - id 不存在时静默 no-op
     */
    suspend fun deleteEvent(id: Long)

    /**
     * 清理某日期之前的所有事件（维护用，例如清理过期事件）。
     */
    suspend fun deleteEventsBefore(date: Long)

    // ===== 联动聚合（Sub-step 2） =====

    /**
     * 观察单日详情：当天的事件 + 未完成任务 + 当天的课程（按周次规则过滤）。
     *
     * 三个数据源用 [kotlinx.coroutines.flow.combine] 合成单一 Flow<DayDetail>。
     * UI 层（DayDetailSheet）直接 collect。
     *
     * @param date 当天 00:00 时间戳
     */
    fun observeDayDetail(date: Long): Flow<DayDetail>

    /**
     * 观察月范围每日汇总（轻量版，只含计数 + today 标志）。
     *
     * UI 层（MonthCalendarGrid）按 date 索引 O(1) 取 badge 数据。
     * 空日期不会出现在 Map 里。
     *
     * @param monthStart 当月第一天 00:00 时间戳（包含）
     * @param monthEnd 下月第一天 00:00 时间戳（不包含）
     * @param today 今日 00:00 时间戳（用于 isToday 标志）
     */
    fun observeMonthSummary(monthStart: Long, monthEnd: Long, today: Long): Flow<MonthSummary>

    // ===== Phase 8 Sub-step 3 全局搜索 =====

    /**
     * 关键字搜索事件（在 title / note / category 任一字段 LIKE 匹配）。
     *
     * - [keyword] 接收**已包好 `%...%` 通配符**的字符串
     * - 区分大小写
     */
    fun searchEvents(keyword: String): Flow<List<Event>>

    // ===== Phase 20.1：清单项时间段 =====

    /**
     * 给清单项设置 / 清除时间段（`tasks.startAt` / `tasks.endAt`）。
     *
     * - 两个都传 null = 清除时间段
     * - id 不存在时静默 no-op
     *
     * ⚠️ 这是日历模块对 `tasks` 表的**唯一写入口**（联动聚合需要读清单项，
     * 「写回去」也放在同一处，免得同一个表有两个写入口）。
     */
    suspend fun setTaskTimeRange(taskId: Long, startAt: Long?, endAt: Long?)
}