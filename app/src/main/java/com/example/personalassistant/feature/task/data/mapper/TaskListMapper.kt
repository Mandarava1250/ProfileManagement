package com.example.personalassistant.feature.task.data.mapper

import com.example.personalassistant.feature.task.data.local.TaskListEntity
import com.example.personalassistant.feature.task.data.local.TaskListWithCountRow
import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.model.TaskListWithCount

/**
 * Phase 17：TaskListEntity ↔ TaskList 双向映射
 */
internal fun TaskListEntity.toDomain(): TaskList = TaskList(
    id = id,
    nameNormalized = nameNormalized,
    displayName = displayName,
    colorHex = colorHex,
    iconKey = iconKey,
    sortOrder = sortOrder,
    isSystem = isSystem,
    isArchived = isArchived,
    createdAt = createdAt,
)

/**
 * Phase 17：清单 + 未完成数的投影行 → domain
 */
internal fun TaskListWithCountRow.toDomain(): TaskListWithCount = TaskListWithCount(
    list = TaskList(
        id = id,
        nameNormalized = nameNormalized,
        displayName = displayName,
        colorHex = colorHex,
        iconKey = iconKey,
        sortOrder = sortOrder,
        isSystem = isSystem,
        isArchived = isArchived,
        createdAt = createdAt,
    ),
    pendingCount = pendingCount,
)
