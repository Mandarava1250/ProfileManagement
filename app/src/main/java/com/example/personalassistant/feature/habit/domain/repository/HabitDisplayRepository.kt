package com.example.personalassistant.feature.habit.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Phase 23：习惯的显示偏好
 *
 * ## 23.6「习惯与日历同屏」开关
 *
 * 这个开关**由习惯模块自己持有**（DataStore key = `habit_show_in_calendar`），
 * 日历侧通过 [com.example.personalassistant.feature.calendar.domain.model.CalendarDisplayPreferences.showHabits]
 * 读同一个 key —— 习惯页和日历页操作的是同一个开关，用户只看到一个。
 *
 * Phase 24B 起，日历侧已经真的把习惯打卡渲染出来了
 * （日视图「习惯打卡」卡片 / 月视图「习惯」标签行 / 月网格角标 / 周视图全天标签区），
 * 这个布尔值不再是占位。
 */
interface HabitDisplayRepository {

    /** 习惯是否在日历里同屏显示（默认 false = 不显示） */
    fun observeShowInCalendar(): Flow<Boolean>

    /** 设置「日历同屏显示」开关 */
    suspend fun setShowInCalendar(enabled: Boolean)
}
