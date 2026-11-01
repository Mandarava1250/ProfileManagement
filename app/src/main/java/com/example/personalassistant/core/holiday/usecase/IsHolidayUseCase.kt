package com.example.personalassistant.core.holiday.usecase

import java.time.LocalDate

/**
 * Phase 8 Sub-step 7：判断指定日期是否为法定假日。
 *
 * 避让语义的核心入口。Reminder 链路在调度前调用本 UseCase，
 * 返回 true 时跳过本次提醒。
 *
 * 与 [IsWorkdayOverrideUseCase] 不同：本 UseCase 只看 HOLIDAY 标记，
 * 不考虑调休工作日（WORKDAY 视为工作日不避让）。
 *
 * Phase 8 Sub-step 7：声明为 `interface` 以便测试 stub 覆盖（无需引入 mock 框架）。
 * 生产实现在 [IsHolidayUseCaseImpl]。
 */
fun interface IsHolidayUseCase {
    suspend operator fun invoke(date: LocalDate): Boolean
}