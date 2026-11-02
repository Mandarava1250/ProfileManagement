package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 13 → 14：性能索引补全（纯性能改动，零行为变更）
 *
 * ## 目标
 *
 * 补 4 条索引，让「按转账配对 id 删转账」「按 (账户, 分类) 计数引用」
 * 「取活跃任务列表」「按分类筛清单项」这四条高频查询不再退化成全表扫。
 *
 * | 索引 | 服务哪条查询 |
 * |---|---|
 * | `index_transactions_transferPairId` | `FinanceDao.getTransferPartner` / `deleteTransactionsByPairId` |
 * | `index_transactions_accountId_categoryId` | `FinanceDao.countTransactionsByCategory`（删分类前的引用计数） |
 * | `index_tasks_isCompleted_priority_dueDate` | `TaskDao.observeActive`（`WHERE isCompleted = 0 ORDER BY priority DESC, dueDate ASC`） |
 * | `index_sub_tasks_category` | `TaskDao.observeTopLevelSubTasksByCategory` |
 *
 * ## 为什么这次最简单
 *
 * 之前几次迁移（[Migration_9_10] / [Migration_11_12]）都要**重建表**，
 * 因为要改已有列的 NOT NULL / DEFAULT。本轮只加索引 ——
 * SQLite 的 `CREATE INDEX` 不动表结构、不动列、不动数据、不动外键，
 * 线上用户的数据一个字节都不会变。
 *
 * ## ⚠️ 索引名必须与 Room 自动生成的**逐字一致**
 *
 * Room 按 `index_<表名>_<列1>_<列2>` 的规则生成默认索引名，
 * 迁移校验（`runMigrationsAndValidate`）会逐个比对**索引名 + 列集合**，
 * 名字或列顺序差一个字符 → 直接抛
 * `Migration didn't properly handle: transactions`（线上闪退同款）。
 *
 * 所以这里的四条语句**不是手写想当然** —— 它们与
 * `TransactionEntity` / `TaskEntity` / v13 时期的 `sub_tasks` 表上 `indices = [...]`
 * 的声明一一对应，由 KSP 生成的 `app/schemas/14.json` 反向校对过。
 *
 * 改名规则 = `index_` + 表名 + `_` + 各列名用 `_` 连接（列序与声明序一致）。
 *
 * ## 幂等
 *
 * 全部用 `CREATE INDEX IF NOT EXISTS`。对已经是 v14 的库再跑一次，
 * 形状与数据都不变。
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_13_14: Migration = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        migration13To14Statements().forEach { database.execSQL(it) }
    }
}

/** 流水表名 */
internal const val TRANSACTIONS_TABLE = "transactions"

/** 任务表名 */
internal const val TASKS_TABLE = "tasks"

/**
 * v14 新增的 4 条索引语句（按序执行）。
 *
 * 抽成函数供 JVM 单测逐条断言 —— 沿用 [Migration_12_13] / [Migration_11_12] 的做法：
 * 索引名写错是**编译期完全看不出来**的错，只在迁移真跑时才炸，
 * 所以必须在单测里把名字钉死。
 */
internal fun migration13To14Statements(): List<String> = listOf(
    // 删一笔转账：按配对 id 精确定位那一对流水
    "CREATE INDEX IF NOT EXISTS `index_transactions_transferPairId` " +
        "ON `$TRANSACTIONS_TABLE` (`transferPairId`)",
    // 删分类前的引用计数：按 (账户, 分类) 过滤
    "CREATE INDEX IF NOT EXISTS `index_transactions_accountId_categoryId` " +
        "ON `$TRANSACTIONS_TABLE` (`accountId`, `categoryId`)",
    // 活跃任务列表：过滤 + 排序一次做完
    // ⚠️ priority 必须是 DESC —— 升序索引用不上「priority DESC」的排序，
    //    SQLite 仍会为命中行另建临时 B-tree 排序（实测见 artifacts/explain_tasks_index_search.py）。
    //    方向后缀照抄 Room 生成的 createSql（14.json）—— Room 迁移校验会比对索引的排序方向。
    "CREATE INDEX IF NOT EXISTS `index_tasks_isCompleted_priority_dueDate` " +
        "ON `$TASKS_TABLE` (`isCompleted` ASC, `priority` DESC, `dueDate` ASC)",
    // 清单项按分类筛选
    "CREATE INDEX IF NOT EXISTS `index_sub_tasks_category` " +
        "ON `${SUB_TASKS_TABLE}` (`category`)",
)
