package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 17 → 18：批次 C 零钱池
 *
 * ## 目标
 *
 * 新建一张表 `coin_pools`（账户零用钱余额）：
 * - `accountId` 唯一（一个账户一个零钱池）+ 外键 → `user_profiles.id`（删账户连带删）
 * - `balance` 可负（欠账）
 * - `note` 可空
 *
 * ## ⚠️ 只 CREATE，不动任何已有表
 *
 * 本次迁移**不碰** transactions / daily_pools / monthly_budgets / categories 等任何已有表 ——
 * 已有表结构一个字节都不改，所以升级路径上没有数据搬迁的风险。
 *
 * ## 表形状必须与 Room 生成的 DDL 逐字一致
 *
 * Room 在编译期生成 `18.json`（schema 快照），运行期靠 `TableInfo` 比对
 * 「迁移出来的表」与「Entity 声明的表」是否一致 —— 不一致就抛
 * `Migration didn't properly handle: coin_pools`。
 *
 * 要点：
 * - `id` 用 `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`（对应 `autoGenerate = true`）
 * - `Double` → `REAL NOT NULL`；`String?` → `TEXT`（可空、无 NOT NULL）
 * - ⚠️ `accountId` 必须带 `DEFAULT 1` —— 实体上写了 `@ColumnInfo(defaultValue = "1")`，
 *   Room 生成的 DDL 就是 `\`accountId\` INTEGER NOT NULL DEFAULT 1`，
 *   漏了这个 DEFAULT，Room 运行期比对 `TableInfo` 时会判定列不一致，
 *   直接抛 `Migration didn't properly handle: coin_pools`。
 * - 索引名用 Room 的命名规则 `index_coin_pools_accountId`
 * - 外键 `ON UPDATE NO ACTION ON DELETE CASCADE`（删账户连带清零钱池）
 *
 * ## 幂等
 *
 * 表 / 索引全部 `CREATE ... IF NOT EXISTS` —— 重复跑迁移不会炸。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        migration17To18Statements().forEach { database.execSQL(it) }
    }
}

/** 零钱池表名 */
internal const val COIN_POOLS_TABLE = "coin_pools"

/** 账户表名（外键父表） */
internal const val USER_PROFILES_TABLE = "user_profiles"

/**
 * 迁移语句序列（抽成函数供 JVM 单测逐条断言 —— 沿用 [Migration_16_17] 的做法）。
 */
internal fun migration17To18Statements(): List<String> = buildList {
    add(createCoinPoolsTableSql())
    add(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_coin_pools_accountId` " +
            "ON `$COIN_POOLS_TABLE` (`accountId`)",
    )
}

/**
 * v18 的 `coin_pools` 目标表结构（**必须与 Room 生成的形状逐字一致**）。
 */
internal fun createCoinPoolsTableSql(): String = """
    CREATE TABLE IF NOT EXISTS `$COIN_POOLS_TABLE` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `accountId` INTEGER NOT NULL DEFAULT 1,
        `balance` REAL NOT NULL,
        `note` TEXT,
        `createdAt` INTEGER NOT NULL,
        `updatedAt` INTEGER NOT NULL,
        FOREIGN KEY(`accountId`) REFERENCES `$USER_PROFILES_TABLE`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
    )
""".trimIndent()
