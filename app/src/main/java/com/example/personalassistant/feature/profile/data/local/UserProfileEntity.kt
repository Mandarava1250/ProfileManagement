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
 *    账户图标已在 Phase 4 落地（[avatarUri]）；跨账户转账与预算跨账户汇总在 Phase 5 落地。
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

    /**
     * 期初余额（Phase 5，可为负）。
     *
     * ## 为什么余额只存这一个数
     * 「当前余额」**不落库**，它是派生值：
     *   余额 = [initialBalance] + 累计收入 − 累计支出 + 转入净额 − 转出净额
     * 存一份 running balance 的话，用户改/删任意一笔历史流水都要重算余额，
     * 漏一条路径就是一笔永久错账，而且没有任何外部真相能发现它 ——
     * 与 Phase 3 修掉的「静默串账」是同一类病灶。派生值每次从流水现算，
     * 永远不可能和流水不一致。
     *
     * 计算入口见 [com.example.personalassistant.feature.finance.data.local.FinanceDao]
     * 的净额聚合查询（转账类型不参与支出/收入统计，只进余额）。
     *
     * ⚠️ `defaultValue = "0"` 必须与迁移里的 `DEFAULT 0` **逐字一致**：
     * SQLite 不允许给非空表加 `NOT NULL` 列而不带 DEFAULT，所以这条 DEFAULT 必须有；
     * 而 Room 会拿 `PRAGMA table_info.dflt_value`（原文 `0`）与注解比对，
     * 写成 `"0.0"` 或不写都会被判 schema 不匹配 —— 而且这个错**编译期看不出来**，
     * 只在 `runMigrationsAndValidate` 真跑时才爆。对比：`avatarUri` / `transferAccountId`
     * 是可空且无 DEFAULT，那边就必须**不写** defaultValue（写了反而 mismatch）。
     */
    @ColumnInfo(name = "initialBalance", defaultValue = "0")
    val initialBalance: Double = 0.0,
)
