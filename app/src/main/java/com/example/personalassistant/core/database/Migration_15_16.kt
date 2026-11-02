package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 15 → 16：Phase 21「清单 ↔ 记账联动」
 *
 * ## 目标
 *
 * `transactions` 加一列 `linkedTaskId`（可空）+ 一条索引。
 *
 * ## 为什么加列就够了
 *
 * 清单项本来就有 `amount` 字段（Phase 16 合并进来的），所以：
 * - 「从清单一键记账」不需要新表，只要写流水时把 `linkedTaskId` 指回去
 * - 「记账页跳回清单项」顺着 `linkedTaskId` 就能回去
 *
 * ## 为什么**不加外键**
 *
 * 清单项被删时，**流水应该留下** —— 钱花出去的事实不该因为任务被删就消失。
 * 加了外键就是 RESTRICT（删任务时被流水挡住）或 CASCADE（连流水一起删），
 * 两个都不对。所以这里是「弱引用」：删任务只会留下一条指向不存在任务的孤儿指针。
 *
 * ## ADD COLUMN 的坑（Phase 16 踩过）
 *
 * SQLite 规定：**带 `REFERENCES` 的新列，其 DEFAULT 必须是 NULL**。
 * 本列没有外键，可以随便加；但仍**不声明 defaultValue** ——
 * Entity 侧是 `Long?`（无默认），Room 期望 `dflt_value` 为 NULL，
 * 一旦声明 `defaultValue = "NULL"`，Room 会把字符串 "NULL" 写进 schema JSON
 * 与实测值比对，直接判 schema 不匹配。
 */

/** 新增的列 */
internal const val LINKED_TASK_ID_COLUMN = "linkedTaskId"

/**
 * Phase 21：Migration_15_16 的语句列表（纯函数，便于 JVM 单测）
 *
 * 与 [Migration_14_15] 同款做法：语句先抽成纯函数，`migrate` 只负责执行 ——
 * 这样「语句形状对不对」不需要设备就能验。
 */
internal fun migration15To16Statements(): List<String> = listOf(
    // ① 加列（可空、无 DEFAULT、无外键）
    "ALTER TABLE `$TRANSACTIONS_TABLE` ADD COLUMN `$LINKED_TASK_ID_COLUMN` INTEGER",

    // ② 加索引（按清单项查流水）
    "CREATE INDEX IF NOT EXISTS `index_${TRANSACTIONS_TABLE}_$LINKED_TASK_ID_COLUMN` " +
        "ON `$TRANSACTIONS_TABLE` (`$LINKED_TASK_ID_COLUMN`)",
)

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        migration15To16Statements().forEach { db.execSQL(it) }
    }
}
