package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v8 -> v9: 记账域多账户 + 数据隔离（Phase 3）
 *
 * ## schema 变更
 *
 * 1. 新建 `user_profiles`（账户根表）+ 插入保留账户 id=1「默认账户」
 * 2. 记账 4 张表各加一列 `accountId INTEGER NOT NULL DEFAULT 1`
 *    - transactions / categories / monthly_budgets / daily_pools
 *    - 老数据靠 DEFAULT 1 一次性无损落到默认账户
 * 3. 唯一索引必须重建（这是最容易漏的点）
 *    - `index_monthly_budgets_yearMonth`(UNIQUE) -> `index_monthly_budgets_accountId_yearMonth`(UNIQUE)
 *    - `index_daily_pools_date`(UNIQUE) -> `index_daily_pools_accountId_date`(UNIQUE)
 *    - 光加列不改索引，第二个账户写同一个月 / 同一天会直接撞 UNIQUE 失败
 * 4. `transactions.categoryId` 的单列 FK 升级为复合 FK
 *    `(accountId, categoryId) -> categories(accountId, id)`，防止跨账户引用。
 *    SQLite 不能 ALTER 外键 -> 只能走「建新表 -> 搬数据 -> drop -> rename」，
 *    并在新表上重建全部索引。
 *
 * ## 为什么迁移里不写 PRAGMA foreign_keys=OFF
 * - SQLite 规定该 PRAGMA 在事务内是 no-op，而 Room 把整个迁移包在一个事务里 -> 写了也不生效。
 * - 本迁移的语句序列不需要关外键：只 DROP 子表 `transactions`（不触发父表约束），
 *   `INSERT INTO transactions_new SELECT` 时父行（categories accountId=1）已就位。
 *   所以即便设备全程开启 FK，迁移也能通过 —— 这是刻意保持的性质，别在后续改动里破坏它。
 * - 收尾用 `PRAGMA foreign_key_check` 做守卫：若真有违反，抛异常让 Room 回滚整个迁移，
 *   宁可停在 v8 也不要留下坏数据。
 *
 * ## sqlite_sequence 为什么要单独修（以及为什么要条件执行）
 * `transactions` 是 AUTOINCREMENT 表。DROP + RENAME 之后，sqlite_sequence 里
 * `name='transactions_new'` 的条目不会自动跟着改名（RENAME 引用自动更新是 SQLite 3.25
 * 才开始完善的，minSdk=26 的设备可能只带 3.19/3.22）。不修不会丢数据（新表插入走
 * MAX(ROWID)+1，不会撞已存在的 id），但会留下悬空条目，干扰后续迁移再建同名临时表。
 *
 * 注意 `sqlite_sequence` 只在首次向 AUTOINCREMENT 表插入时才被创建：
 * 「装了 App 但从未记过账」的 v8 库里它并不存在，所以修正语句必须先判表存在，
 * 否则直接 UPDATE 会抛 no such table 并回滚整个迁移。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 建表 / 默认账户 / 加列 / 索引 / 搬数据 / drop / rename
        MIGRATION_8_9_STATEMENTS.forEach { database.execSQL(it) }
        // RENAME 之后修自增序列表（表不存在则 no-op）
        fixTransactionAutoIncrementSequence(database)
        assertNoForeignKeyViolation(database)
    }
}

/**
 * 迁移语句序列（抽成常量供 JVM 单测逐条断言 —— 计划里的 A 方案兜底回归网）。
 *
 * 顺序有依赖，不要重排：
 * ① user_profiles 建表 + 默认账户 -> ② 4 张表加列 -> ③ categories 复合唯一索引（FK parent 侧必须先有）
 * -> ④ transactions 重建换复合 FK + 索引 -> ⑤ 预算 / 零钱池唯一索引重建
 */
internal val MIGRATION_8_9_STATEMENTS: List<String> = listOf(
    // ===== ① 账户根表 =====
    """
        CREATE TABLE IF NOT EXISTS `user_profiles` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `avatarUri` TEXT,
            `createdAt` INTEGER NOT NULL
        )
    """.trimIndent(),

    // 保留账户 id=1；INSERT OR IGNORE 让它对「已有该 id」的场景幂等
    "INSERT OR IGNORE INTO `user_profiles` (`id`, `name`, `avatarUri`, `createdAt`) " +
        "VALUES (1, '默认账户', NULL, CAST(strftime('%s','now') AS INTEGER) * 1000)",

    // ===== ② 4 张表加 accountId（DEFAULT 1 承接全部旧数据）=====
    "ALTER TABLE `transactions` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 1",
    "ALTER TABLE `categories` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 1",
    "ALTER TABLE `monthly_budgets` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 1",
    "ALTER TABLE `daily_pools` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 1",

    // ===== ③ categories：按账户检索 + 复合 FK 的 parent 索引 =====
    "CREATE INDEX IF NOT EXISTS `index_categories_accountId` ON `categories` (`accountId`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_accountId_id` " +
        "ON `categories` (`accountId`, `id`)",

    // ===== ④ 重建 transactions 以换复合外键 =====
    """
        CREATE TABLE IF NOT EXISTS `transactions_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `accountId` INTEGER NOT NULL DEFAULT 1,
            `amount` REAL NOT NULL,
            `type` INTEGER NOT NULL,
            `categoryId` INTEGER NOT NULL,
            `date` INTEGER NOT NULL,
            `note` TEXT,
            `createdAt` INTEGER NOT NULL,
            `source` TEXT NOT NULL,
            `attachmentUri` TEXT,
            FOREIGN KEY(`accountId`, `categoryId`)
                REFERENCES `categories`(`accountId`, `id`)
                ON UPDATE CASCADE ON DELETE RESTRICT
        )
    """.trimIndent(),

    "INSERT INTO `transactions_new` " +
        "(`id`, `accountId`, `amount`, `type`, `categoryId`, `date`, `note`, `createdAt`, `source`, `attachmentUri`) " +
        "SELECT `id`, `accountId`, `amount`, `type`, `categoryId`, `date`, `note`, `createdAt`, `source`, `attachmentUri` " +
        "FROM `transactions`",

    // DROP 子表会连带删掉它上面的 5 个索引，所以后面必须逐个重建
    "DROP TABLE `transactions`",
    "ALTER TABLE `transactions_new` RENAME TO `transactions`",

    "CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_date_type` ON `transactions` (`date`, `type`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_type_categoryId_date` " +
        "ON `transactions` (`type`, `categoryId`, `date`)",
    "CREATE INDEX IF NOT EXISTS `index_transactions_accountId_date` " +
        "ON `transactions` (`accountId`, `date`)",

    // ===== ⑤ 唯一索引换账户维度（先 DROP 旧的，否则新账户写不进去）=====
    "DROP INDEX IF EXISTS `index_monthly_budgets_yearMonth`",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_budgets_accountId_yearMonth` " +
        "ON `monthly_budgets` (`accountId`, `yearMonth`)",
    "DROP INDEX IF EXISTS `index_daily_pools_date`",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_pools_accountId_date` " +
        "ON `daily_pools` (`accountId`, `date`)",
)

/** RENAME 之后遗留的自增序列条目（sqlite_sequence 可能尚未创建） */
internal const val SQL_FIX_SQLITE_SEQUENCE_NAME: String =
    "UPDATE sqlite_sequence SET `name` = 'transactions' WHERE `name` = 'transactions_new'"

/** 自增水位低于现有最大 id 时抬高，保住 AUTOINCREMENT「永不复用旧 id」语义 */
internal const val SQL_FIX_SQLITE_SEQUENCE_WATERMARK: String =
    "UPDATE sqlite_sequence SET `seq` = (SELECT IFNULL(MAX(`id`), 0) FROM `transactions`) " +
        "WHERE `name` = 'transactions' " +
        "AND `seq` < (SELECT IFNULL(MAX(`id`), 0) FROM `transactions`)"

/**
 * 修正 RENAME 后遗留的自增序列条目。
 *
 * 必须先确认 `sqlite_sequence` 存在：SQLite 只在首次向 AUTOINCREMENT 表插入行时才建这张表，
 * 「装了但从未记过账」的 v8 库里它不存在，直接 UPDATE 会抛 no such table 并回滚整个迁移。
 */
private fun fixTransactionAutoIncrementSequence(database: SupportSQLiteDatabase) {
    val hasSequenceTable = database.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_sequence'",
    ).use { it.count > 0 }
    if (!hasSequenceTable) return

    database.execSQL(SQL_FIX_SQLITE_SEQUENCE_NAME)
    database.execSQL(SQL_FIX_SQLITE_SEQUENCE_WATERMARK)
}

/**
 * 迁移收尾守卫：`PRAGMA foreign_key_check` 有任何输出行即视为数据损坏风险，抛异常让 Room 回滚。
 *
 * 注意：该 PRAGMA 是只读检查，在迁移事务内可用（不像 `foreign_keys` 开关那样受事务限制）。
 */
private fun assertNoForeignKeyViolation(database: SupportSQLiteDatabase) {
    database.query("PRAGMA foreign_key_check").use { cursor ->
        if (cursor.count > 0) {
            val offender = if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                "unknown"
            }
            throw IllegalStateException(
                "Migration_8_9 aborted: foreign_key_check found ${cursor.count} violation(s), " +
                    "first table=$offender. Database stays at v8 (no partial write).",
            )
        }
    }
}
