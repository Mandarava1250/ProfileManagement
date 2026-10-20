package com.example.personalassistant.feature.me.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.personalassistant.feature.profile.data.local.AccountIds
import kotlinx.serialization.Serializable

/**
 * 「我」档案实体
 *
 * 设计要点：
 * - accountId 作为主键（一个账户一份档案）
 * - 不引用 courses 表（成绩由 GPA 计算器从 CourseRepository 拉取）
 * - gpaTarget 默认 4.0（中国 4.0 制 / 美国 4.0 制）
 *
 * Phase 12 新增
 */
@Entity(tableName = "student_profile")
@Serializable
data class StudentProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "accountId")
    val accountId: Long = AccountIds.DEFAULT_ACCOUNT_ID,

    /** 目标 GPA（默认 4.0） */
    @ColumnInfo(name = "gpaTarget")
    val gpaTarget: Double = 4.0,

    /** 简历 / 个人陈述 */
    @ColumnInfo(name = "resumeText")
    val resumeText: String = "",

    /**
     * GPA 规则 JSON（Phase 12 扩展）
     *
     * 为避免引入 Room migration，新规则以 JSON 字符串塞进单列：
     *  - 空串 → 使用 [GpaRule.DEFAULT]（4.0 制默认转换表）
     *  - 非空 → kotlinx.serialization 反序列化为 [GpaRule]
     *
     * 关于 `@ColumnInfo(defaultValue = "''")`：
     *  - Migration_11_12 实际跑的 SQL 是 `ALTER TABLE student_profile ADD COLUMN gpaRuleJson TEXT DEFAULT ''`
     *  - Room 默认对 Kotlin 非空 String 字段生成的 defaultValue 是 `'undefined'`（Room 的 sentinel）
     *  - 二者不一致 → Room schema 校验抛 `Migration didn't properly handle` 闪退
     *  - 显式声明 `defaultValue = "''"` 让 Room 期望值与已有 migration 落库结果对齐
     */
    @ColumnInfo(name = "gpaRuleJson", defaultValue = "''")
    val gpaRuleJson: String = "",

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),
)
