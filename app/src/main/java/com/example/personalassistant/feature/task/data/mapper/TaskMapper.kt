package com.example.personalassistant.feature.task.data.mapper

import com.example.personalassistant.feature.task.data.local.TaskEntity
import com.example.personalassistant.feature.task.domain.model.RepeatType
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority

/**
 * TaskEntity ↔ Task 的双向映射
 *
 * - toDomain：DB 读取时使用
 * - toEntity：DB 写入时使用
 *
 * Phase 4b：Task 模型持有 `repeatType + repeatDays` 两个独立字段，RepeatRule 对象只在
 * 编辑页 UI 状态里出现。Mapper 不解析 RepeatRule，调用方自行用 [RepeatRule.parseDaysString]
 * / [RepeatRule.formatDaysString] + [RepeatRule.fromRepeatType] 处理。
 */
internal fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    dueDate = dueDate,
    priority = TaskPriority.fromDb(priority),
    isCompleted = isCompleted,
    category = category,
    reminderTime = reminderTime,
    createdAt = createdAt,
    updatedAt = updatedAt,
    repeatType = RepeatType.fromDb(repeatType),
    repeatEndDate = repeatEndDate,
    repeatDays = repeatDays,
    manualOrder = manualOrder,
    holidayStrategy = holidayStrategy,
)

internal fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    dueDate = dueDate,
    priority = priority.dbValue,
    isCompleted = isCompleted,
    category = category,
    reminderTime = reminderTime,
    createdAt = createdAt,
    updatedAt = updatedAt,
    repeatType = repeatType.dbValue,
    repeatEndDate = repeatEndDate,
    repeatDays = repeatDays,
    manualOrder = manualOrder,
    holidayStrategy = holidayStrategy,
)
