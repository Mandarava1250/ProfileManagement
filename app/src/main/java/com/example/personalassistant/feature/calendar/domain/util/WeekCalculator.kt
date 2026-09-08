package com.example.personalassistant.feature.calendar.domain.util

/**
 * Phase 8 Sub-step 9 Step 4：日期 → 学期「第几周」计算器
 *
 * ## 设计
 * - 学期开始日（[semesterStartMillis]）视为第 1 周的第一天（weekNumber = 1）
 * - [dateMillis] < [semesterStartMillis] → 返回 null（学期未开始）
 * - [dateMillis] > [semesterEndMillis] → 返回 null（学期已结束）
 *   **修复了旧实现 (CalendarRepositoryImpl.calculateWeekNumber) 的 endDate 边界 bug**
 * - 否则：weekNumber = (date - startDate) / 7 + 1
 *
 * ## 为何抽出来
 * 旧实现在 CalendarRepositoryImpl 里私有；
 * Sub-step 4 的两个 Conflict UseCase 也需要算周次（用于 `Course.appliesToWeek`），
 * 抽成顶层 object 后共享同一份算法。
 *
 * @param dateMillis 当天 00:00 的毫秒时间戳（与 Event.date 语义一致）
 * @param semesterStartMillis 学期开始日 00:00 的毫秒时间戳
 * @param semesterEndMillis 学期结束日 00:00 的毫秒时间戳
 * @return 1..N 的周次；学期外返回 null
 */
object WeekCalculator {
    /** 一天的毫秒数（与 CalendarRepositoryImpl 旧常量对齐，避免除法误差） */
    const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun weekNumber(
        dateMillis: Long,
        semesterStartMillis: Long,
        semesterEndMillis: Long,
    ): Int? {
        if (dateMillis < semesterStartMillis) return null
        if (dateMillis > semesterEndMillis) return null
        val daysDiff = (dateMillis - semesterStartMillis) / ONE_DAY_MILLIS
        return (daysDiff / 7).toInt() + 1
    }
}