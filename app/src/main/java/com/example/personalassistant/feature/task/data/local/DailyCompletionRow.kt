package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo

/**
 * Phase 22.6：某一天的任务计划 / 完成情况（Room 查询投影）
 *
 * 按 `dueDate` 分组统计 —— 「原计划在该时段的任务数」以**截止日**为准：
 * 任务截止在周一，就算周一的计划数。
 */
data class DailyCompletionRow(
    /** 那天的起始时间戳（毫秒） */
    @ColumnInfo(name = "dayStart") val dayStart: Long,
    /** 计划数 */
    @ColumnInfo(name = "planned") val planned: Int,
    /** 完成数 */
    @ColumnInfo(name = "completed") val completed: Int,
)
