package com.example.personalassistant.core.holiday.model

/**
 * Phase 8 Sub-step 7：重复任务遇到节假日时的处理策略
 *
 * 由 [TaskEntity.holidayStrategy] 字段持久化；用户可在创建/编辑任务时切换。
 *
 * 行为定义（约定于 [ApplyHolidayStrategyUseCase]）：
 * - [SKIP]         落在 HOLIDAY 当天 → 跳过本次提醒（默认）
 * - [ADVANCE]      落在 HOLIDAY 当天 → 提前到上一个非节假日同一时刻
 * - [POSTPONE]     落在 HOLIDAY 当天 → 推迟到下一个非节假日同一时刻
 * - [KEEP_NORMAL]  照常提醒，节假日不避让
 */
enum class HolidayStrategy {
    SKIP,
    ADVANCE,
    POSTPONE,
    KEEP_NORMAL,
}