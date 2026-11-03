package com.example.personalassistant.feature.habit.domain.model

/**
 * Phase 23：习惯目标类型
 *
 * - [DAILY]：每日 N 次（「喝水 8 杯」→ 每天累计够 8 次就算当天达成）
 * - [WEEKLY]：每周 N 次（「跑步 3 次」→ 一周内累计够 3 次就算这周达成）
 *
 * DB 里存 [dbValue] 字符串而不是 Int：
 * 枚举增删顺序会变，字符串不会 —— 老数据不会因为「在中间插了个枚举」而读错。
 */
enum class HabitTargetType(val dbValue: String) {
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    ;

    companion object {
        /** 读不出合法值时兜底成 [DAILY]（最宽松的目标，不会让用户的习惯「凭空达成」） */
        fun fromDb(value: String?): HabitTargetType =
            entries.firstOrNull { it.dbValue == value } ?: DAILY
    }
}
