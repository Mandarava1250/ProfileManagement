package com.example.personalassistant.feature.me.data.mapper

import com.example.personalassistant.feature.me.data.local.StudentProfileEntity
import com.example.personalassistant.feature.me.domain.model.GpaRule
import com.example.personalassistant.feature.me.domain.model.StudentProfile
import kotlinx.serialization.json.Json

/**
 * 解析 GPA 规则 JSON；空串 → 默认 4.0 制
 */
private fun parseGpaRule(json: String): GpaRule = if (json.isBlank()) {
    GpaRule.DEFAULT
} else {
    runCatching { Json.decodeFromString<GpaRule>(json) }.getOrDefault(GpaRule.DEFAULT)
}

private fun encodeGpaRule(rule: GpaRule): String =
    Json.encodeToString(GpaRule.serializer(), rule)

internal fun StudentProfileEntity.toDomain(): StudentProfile = StudentProfile(
    accountId = accountId,
    gpaTarget = gpaTarget,
    resumeText = resumeText,
    gpaRule = parseGpaRule(gpaRuleJson),
    updatedAt = updatedAt,
)

internal fun StudentProfile.toEntity(): StudentProfileEntity = StudentProfileEntity(
    accountId = accountId,
    gpaTarget = gpaTarget,
    resumeText = resumeText,
    gpaRuleJson = encodeGpaRule(gpaRule),
    updatedAt = updatedAt,
)
