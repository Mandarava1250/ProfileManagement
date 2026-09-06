package com.example.personalassistant.feature.task.data.mapper

import com.example.personalassistant.feature.task.data.local.TagEntity
import com.example.personalassistant.feature.task.data.local.TagWithCount
import com.example.personalassistant.feature.task.domain.model.Tag

/**
 * Phase 6c：TagEntity ↔ Tag 双向映射
 *
 * - toDomain：DB 读取时使用
 * - toEntity：DB 写入时使用
 *
 * 设计取舍：Mapper 只做字段对应，不做 nameNormalized 的 trim/lowercase 转换。
 * 调用方（UseCase / Repository）需要保证 displayName 已经 trim 过、nameNormalized 已 lowercase 过。
 */

/**
 * 把 TagEntity 转成 Domain Tag（不含 usageCount）。
 *
 * 用于「按 task 查 tag」这类场景，DAO 已经把 TagEntity 全部字段取出来。
 */
internal fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    nameNormalized = nameNormalized,
    displayName = displayName,
    colorHex = colorHex,
    colorSource = colorSource,
    createdAt = createdAt,
    usageCount = 0,
)

/**
 * 把带频次的投影 TagWithCount 转成 Domain Tag。
 *
 * 用于「标签云」观察流。
 */
internal fun TagWithCount.toDomain(): Tag = Tag(
    id = id,
    nameNormalized = nameNormalized,
    displayName = displayName,
    colorHex = colorHex,
    colorSource = colorSource,
    createdAt = createdAt,
    usageCount = usageCount,
)

/**
 * 把 Domain Tag 转成 TagEntity（不写 usageCount，usageCount 由 JOIN 计算）。
 */
internal fun Tag.toEntity(): TagEntity = TagEntity(
    id = id,
    nameNormalized = nameNormalized,
    displayName = displayName,
    colorHex = colorHex,
    colorSource = colorSource,
    createdAt = createdAt,
)