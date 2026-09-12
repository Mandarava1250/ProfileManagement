package com.example.personalassistant.feature.profile.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Phase 3：账户实体（多账户 + 数据隔离的根表）
 *
 * 记账域 4 张表（transactions / categories / monthly_budgets / daily_pools）
 * 全部通过 `accountId` 关联到本表，实现「一个账户 = 一套独立账本」。
 *
 * - id=1 是保留的「默认账户」，Phase 3 迁移把全部旧数据挂在它下面；
 *   新装由 [com.example.personalassistant.core.database.DatabaseCallback.onCreate] 同步写入。
 * - 本表自身**不参与** accountId 隔离（它就是账户清单）。
 * - 只有记账域受账户隔离影响：task / calendar / timetable / holiday 是全局共享的。
 *
 * ⚠️ 删除账户目前仅在 UI 层限制（仅剩 1 个账户时禁删 + 有流水时二次确认），
 *    数据库层没有对 4 张记账表建 FK，所以「删账户」不会级联删数据。
 *    跨账户转账 / 账户图标 / 预算跨账户汇总留在 Phase 5+。
 */
@Entity(tableName = "user_profiles")
@Serializable
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    /** 头像 URI（可选，Phase 4 接入头像选择器；当前恒为 null） */
    @ColumnInfo(name = "avatarUri")
    val avatarUri: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
)
