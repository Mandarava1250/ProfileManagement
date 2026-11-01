package com.example.personalassistant.feature.timetable.domain.repository

import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule
import kotlinx.coroutines.flow.Flow

/**
 * 课时表仓库接口
 *
 * 课时表是**全局**的（不分学期/账户），一份配置。
 * 用 DataStore 持久化，全应用所有学期共用。
 */
interface TimeSlotRepository {
    /** 观察当前课时表（首次访问时返回默认 12 节） */
    fun observeSchedule(): Flow<TimeSlotSchedule>

    /** 保存整张课时表（替换语义） */
    suspend fun saveSchedule(schedule: TimeSlotSchedule)

    /** 重置为内置默认值（[com.example.personalassistant.feature.timetable.domain.model.Period.DEFAULT_12]） */
    suspend fun resetToDefault()
}
