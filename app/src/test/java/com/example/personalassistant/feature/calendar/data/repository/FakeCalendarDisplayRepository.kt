package com.example.personalassistant.feature.calendar.data.repository

import com.example.personalassistant.feature.calendar.domain.model.CalendarDisplayPreferences
import com.example.personalassistant.feature.calendar.domain.repository.CalendarDisplayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Phase 20.3：单元测试用的内存版 [CalendarDisplayRepository]
 *
 * 跟真实现一样是响应式的 —— 开关一改，流立刻发新值。
 */
class FakeCalendarDisplayRepository : CalendarDisplayRepository {

    private val prefs = MutableStateFlow(CalendarDisplayPreferences())

    /** 当前设置（测试里可以直接断言） */
    val current: CalendarDisplayPreferences get() = prefs.value

    override fun observe(): Flow<CalendarDisplayPreferences> = prefs

    override suspend fun setShowCourses(enabled: Boolean) {
        prefs.value = prefs.value.copy(showCourses = enabled)
    }

    override suspend fun setShowTasks(enabled: Boolean) {
        prefs.value = prefs.value.copy(showTasks = enabled)
    }

    override suspend fun setShowCompleted(enabled: Boolean) {
        prefs.value = prefs.value.copy(showCompleted = enabled)
    }
}
