package com.example.personalassistant.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v7 → v8：日历事件支持 RFC 5545 重复规则（Phase 8 Sub-step 9 Step 3）
 *
 * ## schema 变更
 *
 * ### events 表新增一列：rrule
 * - rrule TEXT（可空）
 * - 存的是 RFC 5545 RRULE 字符串（如 "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE"）
 * - null 表示「不重复」（旧数据天然就是 null，迁移零风险）
 *
 * ### 为什么不做加
 * - 不需要 EXDATE / RDATE 表（项目暂不实现排除日期）
 * - 重复 occurrence 不存表，由 [com.example.personalassistant.feature.calendar.domain.recurrence.RecurrenceExpander] 实时展开
 *   - 优势：修改 RRULE 不需要清理数据；删除 RRULE 也不会残留 occurrence
 *   - 代价：日历查询时实时计算（毫秒级，可接受）
 *
 * ⚠️ 一旦发布不允许删除或修改。
 */
val Migration_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `events` ADD COLUMN `rrule` TEXT DEFAULT NULL"
        )
        // 不需要建索引：rrule 不是查询条件（按 date 查即可，展开在内存里）
    }
}