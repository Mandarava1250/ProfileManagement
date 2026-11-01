package com.example.personalassistant.feature.task.domain.repository

import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.model.TaskViewMode
import kotlinx.coroutines.flow.Flow

/**
 * 任务显示偏好仓库（Phase 8b'）
 *
 * 接口与 Android DataStore 解耦，便于测试替换。实现层用 DataStore 持久化。
 *
 * - [observe] 实时偏好 Flow（首次订阅立即发射默认值）
 * - [setAlias] 改写某象限的展示别名
 * - [setViewMode] 切换视图模式
 * - [selectQuadrant] 设定当前选中象限（[TaskViewMode.BY_QUADRANT] 时生效）；
 *                    传 null 等价于「清空选中」（强制回落到 ALL）
 */
interface TaskDisplayRepository {

    fun observe(): Flow<TaskDisplayPreferences>

    suspend fun setAlias(quadrant: TaskQuadrant, alias: String)

    suspend fun setViewMode(mode: TaskViewMode)

    suspend fun selectQuadrant(quadrant: TaskQuadrant?)
}