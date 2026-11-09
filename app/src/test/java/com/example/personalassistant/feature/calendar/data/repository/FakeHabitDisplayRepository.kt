package com.example.personalassistant.feature.calendar.data.repository

import com.example.personalassistant.feature.habit.domain.repository.HabitDisplayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Phase 20.3：单元测试用的内存版 [HabitDisplayRepository]
 *
 * 日历侧的「习惯打卡」开关复用习惯模块的仓储（key = habit_show_in_calendar），
 * 测试里用这个假实现代替。
 */
class FakeHabitDisplayRepository : HabitDisplayRepository {

    private val showInCalendar = MutableStateFlow(false)

    override fun observeShowInCalendar(): Flow<Boolean> = showInCalendar

    override suspend fun setShowInCalendar(enabled: Boolean) {
        showInCalendar.value = enabled
    }
}
