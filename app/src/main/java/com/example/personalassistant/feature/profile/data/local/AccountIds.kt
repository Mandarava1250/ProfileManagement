package com.example.personalassistant.feature.profile.data.local

/**
 * Phase 3：记账域账户隔离的保留 id。
 *
 * ⚠️ [DEFAULT_ACCOUNT_ID] 必须与
 * [com.example.personalassistant.core.database.Migration_8_9] 里
 * `INSERT OR IGNORE INTO user_profiles (id, ...) VALUES (1, ...)` 的字面量保持一致，
 * 也必须与 [com.example.personalassistant.core.di.CoreModule] 里
 * `DatabaseCallback` 新装 seed 用的 id 一致。三处任一改动都要同步，否则：
 * - 迁移写入的默认账户 id ≠ 实体默认 accountId → 旧数据查不到
 * - 新装无默认账户 → 首次记账撞 FK 失败
 *
 * 同理 [DEFAULT_ACCOUNT_NAME] 与迁移里的 `'默认账户'` 需一致（仅展示文案，不一致不致命）。
 */
object AccountIds {

    /** 保留账户 id：迁移时全部旧数据落到它下面；新装由 DatabaseCallback 建它 */
    const val DEFAULT_ACCOUNT_ID: Long = 1L

    /** 默认账户显示名 */
    const val DEFAULT_ACCOUNT_NAME: String = "默认账户"
}
