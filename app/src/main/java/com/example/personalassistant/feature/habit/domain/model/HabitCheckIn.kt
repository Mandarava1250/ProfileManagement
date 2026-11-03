package com.example.personalassistant.feature.habit.domain.model

/**
 * Phase 23：打卡记录（Domain 模型）
 *
 * - [date]：当天 00:00 的毫秒时间戳（用 [com.example.personalassistant.feature.habit.domain.util.HabitDates] 生成）
 * - [count]：当天累计次数（点一下 +1，可减到 0 = 取消当天打卡）
 * - [note]：补卡备注（可空）
 *
 * 一天一条记录 —— 次数累加在同一条上，这样「今天打了 3 次」只需要一行，
 * 月度打卡表 / 年度热力图直接按 date 聚合即可。
 */
data class HabitCheckIn(
    val id: Long = 0L,
    val habitId: Long,
    val date: Long,
    val count: Int = 0,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
