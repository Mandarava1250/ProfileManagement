package com.example.personalassistant.feature.habit.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Phase 23：习惯的显示偏好
 *
 * ## 23.6「习惯与日历同屏」开关
 *
 * 计划要求复用 Phase 20 日历侧的「显示开关」。但日历那边（[com.example.personalassistant.feature.calendar]）
 * 目前只有「日程 / 任务 / 课程同屏」的实现，**并没有一个通用的日历显示开关**，
 * 而 Phase 23 又不允许改 calendar 模块 —— 所以这里先由习惯模块自己持有这个开关（DataStore 占位）。
 *
 * 等日历侧补上对应开关后，把这里的读写指过去即可，
 * UI 侧（[com.example.personalassistant.feature.habit.presentation]）不用改。
 */
interface HabitDisplayRepository {

    /** 习惯是否在日历里同屏显示（默认 false = 不显示） */
    fun observeShowInCalendar(): Flow<Boolean>

    /** 设置「日历同屏显示」开关 */
    suspend fun setShowInCalendar(enabled: Boolean)
}
