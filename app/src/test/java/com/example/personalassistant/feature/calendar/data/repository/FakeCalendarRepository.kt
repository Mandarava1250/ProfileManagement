package com.example.personalassistant.feature.calendar.data.repository

import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.MonthSummary
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Phase 7c：单元测试用的内存版 [CalendarRepository]（含 Sub-step 2 联动方法的占位实现）
 *
 * ## 设计动机
 * - UseCase 测试要快、要不依赖 Room
 * - 用 MutableStateFlow 模拟 Room 的响应式行为
 * - **联动方法**（observeDayDetail / observeMonthSummary）提供最简单的占位实现：
 *   返回空 DayDetail / 空 Map，让 UseCase 测试关注事件流本身的语义
 *
 * ## 约定
 * - 所有 suspend 写操作同步更新内存 + 触发 flow 重新发射
 * - id 自增（nextId 从 1 开始）
 */
class FakeCalendarRepository : CalendarRepository {

    private val eventsState = MutableStateFlow<List<Event>>(emptyList())
    private var nextId = 1L

    /** 调试用：观察 addEvent 次数 */
    var addCount: Int = 0
        private set
    var deleteCount: Int = 0
        private set
    var updateCount: Int = 0
        private set

    // ===== 单日 / 区间查询 =====

    override fun observeEventsByDate(date: Long): Flow<List<Event>> =
        eventsState.map { list -> list.filter { it.date == date } }

    override fun observeEventsByMonthRange(monthStart: Long, monthEnd: Long): Flow<List<Event>> =
        eventsState.map { list -> list.filter { it.date in monthStart until monthEnd } }

    override fun observeUpcoming(today: Long, futureEnd: Long): Flow<List<Event>> =
        eventsState.map { list -> list.filter { it.date in today until futureEnd } }

    override fun observeEventById(id: Long): Flow<Event?> =
        eventsState.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getEventById(id: Long): Event? =
        eventsState.value.firstOrNull { it.id == id }

    // ===== 增删改 =====

    override suspend fun addEvent(event: Event): Long {
        val id = nextId++
        addCount++
        eventsState.value = eventsState.value + event.copy(
            id = id,
            createdAt = event.createdAt,
            updatedAt = System.currentTimeMillis(),
        )
        return id
    }

    override suspend fun updateEvent(event: Event) {
        if (event.id <= 0L) return
        updateCount++
        eventsState.value = eventsState.value.map {
            if (it.id == event.id) event.copy(updatedAt = System.currentTimeMillis()) else it
        }
    }

    override suspend fun deleteEvent(id: Long) {
        deleteCount++
        eventsState.value = eventsState.value.filter { it.id != id }
    }

    override suspend fun deleteEventsBefore(date: Long) {
        eventsState.value = eventsState.value.filter { it.date >= date }
    }

    // ===== 联动聚合（Sub-step 2） — 占位实现 =====

    override fun observeDayDetail(date: Long): Flow<DayDetail> =
        eventsState.map { _ ->
            // 简化版：只返回事件 + 默认空 tasks/courses
            // 真实场景需要 mock taskDao / courseDao / semesterDao，但这里只测试 events 链路
            DayDetail(
                date = date,
                dayOfWeek = 1,
                events = eventsState.value.filter { it.date == date },
                tasks = emptyList(),
                courses = emptyList(),
                weekNumber = null,
            )
        }

    override fun observeMonthSummary(monthStart: Long, monthEnd: Long, today: Long): Flow<MonthSummary> =
        eventsState.map { _ -> emptyMap() }

    // ===== 测试辅助 helpers =====

    /** 直接注入一条带 id 的事件（跳过自增），便于测试 */
    suspend fun seedEvent(
        title: String = "测试事件",
        date: Long,
        isAllDay: Boolean = false,
        startTime: Long? = null,
        endTime: Long? = null,
        id: Long = 0L,
    ): Long {
        val resolvedId = if (id > 0L) {
            id
        } else {
            nextId++
        }
        eventsState.value = eventsState.value + Event(
            id = resolvedId,
            title = title,
            date = date,
            isAllDay = isAllDay,
            startTime = startTime,
            endTime = endTime,
        )
        return resolvedId
    }

    /** 清空所有事件 */
    fun clear() {
        eventsState.value = emptyList()
    }
}