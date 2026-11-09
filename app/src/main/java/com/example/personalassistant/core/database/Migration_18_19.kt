package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 18 → 19：批次 D 零钱池改日切自动结算
 *
 * ## 目标
 *
 * 1. `coin_pools` 加一列 `lastSettledDate`（结算水位线）
 * 2. 新建一张表 `coin_pool_settlements`（每日结算流水）
 *
 * ## ⚠️ ALTER TABLE 必须带 DEFAULT
 *
 * SQLite 不允许给已有表加「NOT NULL 且没有默认值的列」——
 * `ALTER TABLE ... ADD COLUMN x INTEGER NOT NULL` 会直接报
 * `Cannot add a NOT NULL column with default value NULL`。
 * 所以这里必须写 `NOT NULL DEFAULT 0`：
 *
 * - `0` = 从来没结算过（还没有任何一天的结算记录）
 * - 实体上也声明 `@ColumnInfo(defaultValue = "0")`，
 *   两边一致，Room 运行期比对 `TableInfo` 才不会判成列不一致。
 *
 * ## ⚠️ 新表形状必须与 Room 生成的 DDL 逐字一致
 *
 * Room 在编译期生成 `19.json`（schema 快照），运行期靠 `TableInfo` 比对
 * 「迁移出来的表」与「Entity 声明的表」——不一致就抛
 * `Migration didn't properly handle: coin_pool_settlements`。
 *
 * 要点：
 * - `id` 用 `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`（对应 `autoGenerate = true`）
 * - `Double` → `REAL NOT NULL`；`String` → `TEXT NOT NULL`
 * - ⚠️ `accountId` 必须带 `DEFAULT 1`（实体上写了 `@ColumnInfo(defaultValue = "1")`）——
 *   漏了这个 DEFAULT，运行期直接崩，**编译期看不出来**（v18 就栽在这）
 * - 索引名用 Room 的命名规则 `index_coin_pool_settlements_accountId_date`
 * - 外键 `ON UPDATE NO ACTION ON DELETE CASCADE`（删账户连带清结算流水）
 *
 * ## 幂等
 *
 * 建表 / 建索引用 `CREATE ... IF NOT EXISTS`；`ALTER TABLE ADD COLUMN`
 * 在 SQLite 里没有 `IF NOT EXISTS` 语法 —— 迁移本来就只跑一次，不重复执行。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        migration18To19Statements().forEach { database.execSQL(it) }
    }
}

/** 零钱池结算流水表名 */
internal const val COIN_POOL_SETTLEMENTS_TABLE = "coin_pool_settlements"

/**
 * 迁移语句序列（抽成函数供 JVM 单测逐条断言 —— 沿用 [MIGRATION_17_18] 的做法）。
 */
internal fun migration18To19Statements(): List<String> = buildList {
    add(alterCoinPoolsAddLastSettledDateSql())
    add(createCoinPoolSettlementsTableSql())
    add(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_coin_pool_settlements_accountId_date` " +
            "ON `$COIN_POOL_SETTLEMENTS_TABLE` (`accountId`, `date`)",
    )
}

/**
 * 给 `coin_pools` 加结算水位线列。
 *
 * ⚠️ `NOT NULL DEFAULT 0` 的 `DEFAULT 0` 不能省 ——
 * 实体上声明的是 `@ColumnInfo(defaultValue = "0")`，两边必须一致。
 */
internal fun alterCoinPoolsAddLastSettledDateSql(): String =
    "ALTER TABLE `$COIN_POOLS_TABLE` ADD COLUMN `lastSettledDate` INTEGER NOT NULL DEFAULT 0"

/**
 * v19 的 `coin_pool_settlements` 目标表结构（**必须与 Room 生成的形状逐字一致**）。
 */
internal fun createCoinPoolSettlementsTableSql(): String = """
    CREATE TABLE IF NOT EXISTS `$COIN_POOL_SETTLEMENTS_TABLE` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `accountId` INTEGER NOT NULL DEFAULT 1,
        `date` INTEGER NOT NULL,
        `amount` REAL NOT NULL,
        `ym` TEXT NOT NULL,
        `createdAt` INTEGER NOT NULL,
        FOREIGN KEY(`accountId`) REFERENCES `$USER_PROFILES_TABLE`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
    )
""".trimIndent()
