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
 *
 * ## ⚠️ 三列的 defaultValue 不是装饰，是 Room schema 校验的硬要求
 *
 * 本表的物理结构是 [com.example.personalassistant.core.database.Migration_10_11]
 * **手写 CREATE TABLE** 建出来的（不是 Room 自己建的），所以它的 DEFAULT 与
 * Room 从 Entity 推出来的期望必须**逐字一致**，否则启动时 Room 校验 schema 失败：
 *
 * ```
 * java.lang.IllegalStateException: Migration didn't properly handle: student_profile
 * ```
 *
 * 线上闪退实测（Expected = Room 期望，Found = SQLite 实际）：
 *
 * | 列 | Expected | Found | 对齐方式 |
 * |---|---|---|---|
 * | gpaTarget | dflt='undefined' | dflt='4.0' | `@ColumnInfo(defaultValue = "4.0")` |
 * | resumeText | dflt='undefined' | dflt='' | `@ColumnInfo(defaultValue = "''")` |
 * | gpaRuleJson | notNull=true, dflt='undefined' | **notNull=false**, dflt='' | `String?` + `defaultValue = "''"` |
 *
 * Room 2.6 的列比对是**双向**的：数据库有 DEFAULT 而 Entity 没声明，同样算不匹配。
 * 而 `gpaRuleJson` 是 `ALTER TABLE ... ADD COLUMN` 加出来的 —— SQLite 的 ADD COLUMN
 * **不会**附带 NOT NULL 约束，所以这一列天生 nullable，**`defaultValue` 调不了 notNull**，
 * 只能把 Kotlin 类型改成 `String?`。
 */
@Entity(tableName = "student_profile")
@Serializable
data class StudentProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "accountId")
    val accountId: Long = AccountIds.DEFAULT_ACCOUNT_ID,

    /** 目标 GPA（默认 4.0） */
    @ColumnInfo(name = "gpaTarget", defaultValue = "4.0")
    val gpaTarget: Double = 4.0,

    /** 简历 / 个人陈述 */
    @ColumnInfo(name = "resumeText", defaultValue = "''")
    val resumeText: String = "",

    /**
     * GPA 规则 JSON（Phase 12 扩展）
     *
     * 为避免引入 Room migration，新规则以 JSON 字符串塞进单列：
     *  - 空串 → 使用 [GpaRule.DEFAULT]（4.0 制默认转换表）
     *  - 非空 → kotlinx.serialization 反序列化为 [GpaRule]
     *
     * 之所以是 `String?`：`ALTER TABLE ADD COLUMN` 出来的列天生 nullable，
     * Entity 必须与之保持一致，否则 Room schema 校验不过（见类注释的对照表）。
     * 业务层读到的永远是空串或有效 JSON —— [StudentProfileMappers] 做了兜底。
     */
    @ColumnInfo(name = "gpaRuleJson", defaultValue = "''")
    val gpaRuleJson: String? = "",

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),
)
