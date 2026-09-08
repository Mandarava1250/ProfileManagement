package com.example.personalassistant.feature.calendar.data.mapper

import com.example.personalassistant.feature.calendar.data.local.EventEntity
import com.example.personalassistant.feature.calendar.domain.model.Event

/**
 * Phase 7c：EventEntity ↔ Event 双向映射
 *
 * - toDomain：DB 读出后立刻转 domain，避免 UI/UseCase 看到 Entity
 * - toEntity：DB 写入前转 Entity（自动同步 updatedAt）
 *
 * 设计取舍：Mapper 只做字段对应，不做业务校验（id > 0、date 合法等校验由 UseCase 层处理）。
 */

internal fun EventEntity.toDomain(): Event = Event(
    id = id,
    title = title,
    date = date,
    isAllDay = isAllDay,
    startTime = startTime,
    endTime = endTime,
    color = color,
    reminder = reminder,
    note = note,
    // Phase 8 Sub-step 9：categoryId 优先；旧 category 字段保留（只读兼容）
    categoryId = categoryId,
    category = category,
    linkedTaskId = linkedTaskId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Event.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title,
    date = date,
    isAllDay = isAllDay,
    startTime = startTime,
    endTime = endTime,
    color = color,
    reminder = reminder,
    note = note,
    // Phase 8 Sub-step 9：保存时只写 categoryId；旧 category 字段保留（只读兼容）
    categoryId = categoryId,
    category = category,
    linkedTaskId = linkedTaskId,
    createdAt = createdAt,
    updatedAt = System.currentTimeMillis(),
)