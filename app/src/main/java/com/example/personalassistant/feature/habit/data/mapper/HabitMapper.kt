package com.example.personalassistant.feature.habit.data.mapper

import com.example.personalassistant.feature.habit.data.local.HabitCheckInEntity
import com.example.personalassistant.feature.habit.data.local.HabitEntity
import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitCheckIn
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType

/**
 * Phase 23：Entity ↔ Domain 双向映射
 *
 * - toDomain：DB 读取时使用
 * - toEntity：DB 写入时使用
 *
 * 设计取舍：Mapper 只做字段对应，不做业务判断。
 * `targetType` 的字符串 ↔ 枚举转换放在 [HabitTargetType.fromDb] 里，
 * 读不出合法值时兜底成 DAILY（宁可判「还没达成」，也不让历史数据凭空达成）。
 */
internal fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    iconKey = iconKey,
    color = color,
    targetType = HabitTargetType.fromDb(targetType),
    targetCount = targetCount,
    unit = unit,
    sortOrder = sortOrder,
    isArchived = isArchived,
    createdAt = createdAt,
)

internal fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    color = color,
    targetType = targetType.dbValue,
    targetCount = targetCount,
    unit = unit,
    sortOrder = sortOrder,
    isArchived = isArchived,
    createdAt = createdAt,
)

internal fun HabitCheckInEntity.toDomain(): HabitCheckIn = HabitCheckIn(
    id = id,
    habitId = habitId,
    date = date,
    count = count,
    note = note,
    createdAt = createdAt,
)

internal fun HabitCheckIn.toEntity(): HabitCheckInEntity = HabitCheckInEntity(
    id = id,
    habitId = habitId,
    date = date,
    count = count,
    note = note,
    createdAt = createdAt,
)
