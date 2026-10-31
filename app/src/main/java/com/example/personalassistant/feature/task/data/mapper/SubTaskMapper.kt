package com.example.personalassistant.feature.task.data.mapper

import com.example.personalassistant.feature.task.data.local.SubTaskEntity
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.TaskPriority

/**
 * SubTaskEntity ↔ SubTask 双向映射
 *
 * Phase 15：新增分类 / 截止时间 / 优先级 / 金额 / 嵌套父指针 5 个字段。
 * 优先级在 DB 里存 [TaskPriority.dbValue]（0=低 1=中 2=高 3=普通 4=紧急），
 * Domain 侧用枚举 —— 与 [com.example.personalassistant.feature.task.data.mapper.TaskMapper] 同款做法。
 */
internal fun SubTaskEntity.toDomain(): SubTask = SubTask(
    id = id,
    parentTaskId = parentTaskId,
    title = title,
    isCompleted = isCompleted,
    order = order,
    createdAt = createdAt,
    category = category,
    dueDate = dueDate,
    priority = TaskPriority.fromDb(priority),
    amount = amount,
    parentSubTaskId = parentSubTaskId,
)

internal fun SubTask.toEntity(): SubTaskEntity = SubTaskEntity(
    id = id,
    parentTaskId = parentTaskId,
    title = title,
    isCompleted = isCompleted,
    order = order,
    createdAt = createdAt,
    category = category,
    dueDate = dueDate,
    priority = priority.dbValue,
    amount = amount,
    parentSubTaskId = parentSubTaskId,
)
