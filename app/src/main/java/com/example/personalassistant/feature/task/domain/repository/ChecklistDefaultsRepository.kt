package com.example.personalassistant.feature.task.domain.repository

import com.example.personalassistant.feature.task.domain.model.ChecklistDefaults
import kotlinx.coroutines.flow.Flow

/**
 * Phase 22.5：新建清单项的默认参数
 *
 * 走 DataStore（**不新增 Room 表**）—— 这是一组纯偏好，
 * 跟业务数据无关，不该占数据库的位置。
 */
interface ChecklistDefaultsRepository {

    /** 观察当前默认值。 */
    fun observe(): Flow<ChecklistDefaults>

    /** 覆盖保存（null 表示「不设默认清单」/「不设默认提醒」）。 */
    suspend fun set(defaults: ChecklistDefaults)
}
