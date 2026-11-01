package com.example.personalassistant.feature.me.data.mapper

import com.example.personalassistant.feature.me.data.local.StudentProfileEntity
import com.example.personalassistant.feature.me.domain.model.GpaRule
import com.example.personalassistant.feature.me.domain.model.StudentProfile
import kotlinx.serialization.json.Json

/**
 * 解析 GPA 规则 JSON；空串 / null → 默认 4.0 制
 *
 * 之所以接受 null：`gpaRuleJson` 是 `ALTER TABLE ADD COLUMN` 加出来的列，
 * SQLite 的 ADD COLUMN 不附带 NOT NULL 约束 → 该列天生 nullable →
 * Entity 侧也必须是 `String?`（否则 Room schema 校验不过）。
 * 业务语义上永远拿不到 null 的坏值：null 与空串等价，都落回默认规则。
 */
private fun parseGpaRule(json: String?): GpaRule = if (json.isNullOrBlank()) {
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
