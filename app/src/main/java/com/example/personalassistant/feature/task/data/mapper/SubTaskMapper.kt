package com.example.personalassistant.feature.task.data.mapper

import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.domain.model.SubTask

/** SubTaskEntity ↔ SubTask 双向映射 */
internal fun SubTaskEntity.toDomain(): SubTask = SubTask(
    id = id,
    parentTaskId = parentTaskId,
    title = title,
    isCompleted = isCompleted,
    order = order,
    createdAt = createdAt,
)

internal fun SubTask.toEntity(): SubTaskEntity = SubTaskEntity(
    id = id,
    parentTaskId = parentTaskId,
    title = title,
    isCompleted = isCompleted,
    order = order,
    createdAt = createdAt,
)
