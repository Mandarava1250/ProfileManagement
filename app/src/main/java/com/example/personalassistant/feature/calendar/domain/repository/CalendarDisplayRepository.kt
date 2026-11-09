package com.example.personalassistant.feature.calendar.domain.repository

import com.example.personalassistant.feature.calendar.domain.model.CalendarDisplayPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Phase 20.3：日历「显示设置」的读写入口
 *
 * ## 范围
 *
 * 这里只管日历模块自己的三个开关（课程 / 清单项 / 已完成）。
 *
 * 「习惯打卡」开关**不在这里** —— 它复用习惯模块的
 * [com.example.personalassistant.feature.habit.domain.repository.HabitDisplayRepository]
 * （key = `habit_show_in_calendar`），这样习惯页和日历页操作的是同一个开关。
 *
 * ## 实现
 *
 * DataStore（见 [com.example.personalassistant.feature.calendar.data.preferences.CalendarDisplayRepositoryImpl]），
 * **不新增 Room 表** —— 这是显示偏好，不是业务数据。
 */
interface CalendarDisplayRepository {

    /** 观察当前显示设置（默认值见 [CalendarDisplayPreferences]）。 */
    fun observe(): Flow<CalendarDisplayPreferences>

    /** 课程在日历里显不显示。 */
    suspend fun setShowCourses(enabled: Boolean)

    /** 清单项在日历里显不显示。 */
    suspend fun setShowTasks(enabled: Boolean)

    /** 已完成的清单项在日历里显不显示。 */
    suspend fun setShowCompleted(enabled: Boolean)
}
