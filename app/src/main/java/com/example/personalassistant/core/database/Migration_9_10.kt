package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.personalassistant.feature.finance.data.seed.DefaultCategories

/**
 * v9 -> v10: 跨账户转账 + 账户余额（Phase 5）
 *
 * ## schema 变更（三条纯增量，**不重建任何表**）
 * 1. `transactions` 加 `transferAccountId INTEGER DEFAULT NULL`（转账对方账户 id）
 *    与 `transferPairId TEXT DEFAULT NULL`（转账配对 id，两行共享）
 * 2. `transactions` 加索引 `index_transactions_accountId_type`（Entity 里声明了，
 *    迁移不建就会 `runMigrationsAndValidate` 报 missing index）
 * 3. `user_profiles` 加 `initialBalance REAL NOT NULL DEFAULT 0` —— 期初余额
 * 4. 给**每个存量账户**补一条内置「转账」分类（`categories.type = 2`）
 *
 * ## 为什么这次不需要「建新表 → 搬数据 → drop → rename」
 * Phase 3 之所以要重建 transactions，是因为要把单列 FK 升级成复合 FK，
 * 而 SQLite 不支持 ALTER 外键。本轮只加列和索引，两者都能直接 ALTER，
 * 所以旧表数据一行都不动 —— 风险面比 Migration_8_9 小一个数量级。
 *
 * ## 第 4 步为什么必须有（本轮最容易漏的点）
 * `transactions.categoryId` 是 NOT NULL 且对 `categories(accountId, id)` 带
 * RESTRICT 外键。转账流水也必须挂一个**属于本账户、真实存在**的分类行，
 * 而老库里每个账户只有 8 支出 + 1 收入，没有任何可安全复用的行：
 * 复用「其他」会让转账混进支出统计，新建一条又只覆盖了默认账户。
 * 所以迁移要给**每一个**现存账户各补一条 type=2 的分类，
 * 否则用户升级后第一次转账就直接撞外键失败。
 *
 * 用 `INSERT ... SELECT ... FROM user_profiles` 一条语句覆盖全部账户，
 * 配 `WHERE NOT EXISTS` 保证幂等（重复跑 / 未来重放都安全）。
 *
 * ## 为什么新列的 DEFAULT 写法不一样
 * - `transferAccountId` 可空 → `DEFAULT NULL`，与 [Migration_6_7] 的
 *   `events.categoryId`、[Migration_7_8] 的 `events.rrule` 同构。
 *   Room 的 schema JSON 对这类列**不写** `defaultValue` 键，
 *   Entity 侧也必须不声明 `@ColumnInfo(defaultValue = ...)`，两边才对得上。
 * - `initialBalance` NOT NULL → 必须带 DEFAULT（SQLite 不允许给非空表加
 *   NOT NULL 列而不给默认值），且写 `DEFAULT 0` 与 Entity 的
 *   `defaultValue = "0"` **逐字一致**。写成 `0.0` 就会 mismatch，
 *   而这个错编译期完全看不出来，只在迁移真跑时才爆。
 *
 * ## 为什么这里不 seed 收支分类
 * 与 [Migration_8_9] 一致：`onUpgrade` 不重复 seed 内置收支分类，
 * 避免覆盖用户后续改名。转账分类是新增的、用户不可能改过，所以可以安全补。
 *
 * ## 为什么不给 transferPairId 建索引
 * 它只在「删一笔转账」时作为 WHERE 条件用一次，而转账行数 = 用户主动转账次数
 * （量级远小于普通流水）。为一个删除操作维护索引不划算，
 * 真要加时再走一次迁移即可。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        MIGRATION_9_10_STATEMENTS.forEach { database.execSQL(it) }
        assertNoForeignKeyViolation(database)
    }
}

/**
 * 迁移语句序列（抽成常量供 JVM 单测逐条断言 —— 沿用 [MIGRATION_8_9_STATEMENTS] 的做法）。
 *
 * 顺序有依赖，不要重排：
 * ① user_profiles 加 initialBalance → ② transactions 加 transferAccountId
 * → ③ 建索引 → ④ 给所有账户补转账分类（依赖 categories 表结构未变，放最后最安全）
 */
internal val MIGRATION_9_10_STATEMENTS: List<String> = listOf(
    // ===== ① 期初余额 =====
    "ALTER TABLE `user_profiles` ADD COLUMN `initialBalance` REAL NOT NULL DEFAULT 0",

    // ===== ② 转账对方账户 =====
    "ALTER TABLE `transactions` ADD COLUMN `transferAccountId` INTEGER DEFAULT NULL",

    // 转账配对 id（两行共享一个 UUID，精确删除用）
    "ALTER TABLE `transactions` ADD COLUMN `transferPairId` TEXT DEFAULT NULL",

    // ===== ③ Entity 新增的复合索引 =====
    "CREATE INDEX IF NOT EXISTS `index_transactions_accountId_type` " +
        "ON `transactions` (`accountId`, `type`)",

    // ===== ④ 每个存量账户补一条内置「转账」分类 =====
    // 值全部来自 DefaultCategories 常量，避免与 Kotlin 侧 seed 漂移：
    // 两处各写字面量的话，改一处忘一处不会报错，只会让老库和新装的分类悄悄不同。
    """
        INSERT INTO `categories`
            (`accountId`, `name`, `type`, `iconName`, `color`, `isDefault`, `sortOrder`)
        SELECT up.`id`,
               '${DefaultCategories.TRANSFER_CATEGORY_NAME}',
               ${DefaultCategories.CATEGORY_TYPE_TRANSFER},
               '${DefaultCategories.TRANSFER_ICON_NAME}',
               ${DefaultCategories.TRANSFER_CATEGORY_COLOR},
               1,
               0
        FROM `user_profiles` up
        WHERE NOT EXISTS (
            SELECT 1 FROM `categories` c
            WHERE c.`accountId` = up.`id`
              AND c.`type` = ${DefaultCategories.CATEGORY_TYPE_TRANSFER}
        )
    """.trimIndent(),
)

/**
 * 迁移收尾守卫：`PRAGMA foreign_key_check` 有任何输出行即视为数据损坏风险，抛异常让 Room 回滚。
 *
 * 与 [Migration_8_9] 里的同名私有函数逻辑一致，但**故意不抽成共用函数**：
 * 那个迁移已发布（已 push 且在 AVD 真跑过），台账明写「一旦发布不允许修改」，
 * 为消 12 行重复而去动一个已验证的迁移，风险收益不划算；
 * 第三份同类代码出现时再统一提取。
 *
 * 注意：该 PRAGMA 是只读检查，在迁移事务内可用（不像 `foreign_keys` 开关那样受事务限制）。
 */
private fun assertNoForeignKeyViolation(database: SupportSQLiteDatabase) {
    database.query("PRAGMA foreign_key_check").use { cursor ->
        if (cursor.count > 0) {
            val offender = if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
            throw IllegalStateException(
                "Migration_9_10 aborted: foreign_key_check found ${cursor.count} violation(s), " +
                    "first table=$offender. Database stays at v9 (no partial write).",
            )
        }
    }
}
