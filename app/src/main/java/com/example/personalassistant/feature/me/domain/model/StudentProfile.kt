package com.example.personalassistant.feature.me.domain.model

/**
 * 「我」档案（领域模型）
 *
 *  - [accountId] 主键，账号隔离
 *  - [gpaTarget] 目标 GPA（默认 4.0；UI 可以改 5.0 制或百分制）
 *  - [resumeText] 简历 / 个人陈述（多行）
 *  - [gpaRule] GPA 计算规则（学校定制，方案 2 强模型：scaleMax / scaleMin / 转换表）
 */
data class StudentProfile(
    val accountId: Long,
    val gpaTarget: Double,
    val resumeText: String,
    val gpaRule: GpaRule = GpaRule.DEFAULT,
    val updatedAt: Long,
)
