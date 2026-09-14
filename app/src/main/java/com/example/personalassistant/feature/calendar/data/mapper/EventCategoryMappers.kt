package com.example.personalassistant.feature.calendar.data.mapper

import com.example.personalassistant.feature.calendar.data.local.EventCategoryEntity
import com.example.personalassistant.feature.calendar.domain.model.EventCategory

/**
 * Phase 8 Sub-step 9：EventCategoryEntity ↔ EventCategory 双向映射
 *
 * - toDomain：DB → Domain（Mapper 入口收口）
 * - toEntity：Domain → DB
 */
internal fun EventCategoryEntity.toDomain(): EventCategory = EventCategory(
    id = id,
    name = name,
    color = color,
    iconKey = iconKey,
    sortOrder = sortOrder,
    isDefault = isDefault,
)

internal fun EventCategory.toEntity(): EventCategoryEntity = EventCategoryEntity(
    id = id,
    name = name,
    color = color,
    iconKey = iconKey,
    sortOrder = sortOrder,
    isDefault = isDefault,
)
