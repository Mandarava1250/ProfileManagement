package com.example.personalassistant.core.holiday.usecase

import com.example.personalassistant.core.holiday.data.HolidayRepository
import com.example.personalassistant.core.holiday.model.HolidayStrategy
import com.example.personalassistant.core.holiday.model.HolidayType
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs

/**
 * Phase 8 Sub-step 7：应用节假日策略到重复任务的某次 occurrence。
 *
 * ## 调用时机
 * - TaskOccurrenceExpander 在展开重复任务时,对每个 occurrence 调用一次
 * - Event 不接入本 UseCase(一次性事件不需要策略)
 *
 * ## 策略语义
 * - [HolidayStrategy.SKIP]         落在 HOLIDAY → return null(跳过本次)
 * - [HolidayStrategy.ADVANCE]      落在 HOLIDAY → 找上一个非 HOLIDAY 同一天同时刻
 * - [HolidayStrategy.POSTPONE]     落在 HOLIDAY → 找下一个非 HOLIDAY 同一天同时刻
 * - [HolidayStrategy.KEEP_NORMAL]  照常 return date
 *
 * 「同时刻」在本 UseCase 里简化为「同一天」:避免改 LocalDateTime
 * (任务 reminder 通常是按 day granularity 触发)。
 */
class ApplyHolidayStrategyUseCase @Inject constructor(
    private val holidayRepository: HolidayRepository,
) {
    /**
     * @param occurrence 原始发生日期
     * @param strategy 用户在任务上配置的策略
     * @return 调整后的日期;若 SKIP 且命中 HOLIDAY → null
     */
    suspend operator fun invoke(
        occurrence: LocalDate,
        strategy: HolidayStrategy,
    ): LocalDate? {
        return when (strategy) {
            HolidayStrategy.SKIP -> if (isHoliday(occurrence)) null else occurrence
            HolidayStrategy.ADVANCE -> if (isHoliday(occurrence)) findPreviousWorkday(occurrence) else occurrence
            HolidayStrategy.POSTPONE -> if (isHoliday(occurrence)) findNextWorkday(occurrence) else occurrence
            HolidayStrategy.KEEP_NORMAL -> occurrence
        }
    }

    private suspend fun isHoliday(date: LocalDate): Boolean =
        holidayRepository.getHolidaysByDate(date).any { it.type == HolidayType.HOLIDAY }

    /**
     * 向前找最近的非 HOLIDAY 日（最多回溯 7 天保护性能）。
     *
     * 7 天保证：若某周全是 HOLIDAY（理论上春节 7 天假），
     * ADVANCE 会回溯到春节开始前的最后一个工作日。
     */
    private suspend fun findPreviousWorkday(date: LocalDate): LocalDate {
        var d = date
        for (i in 1..7) {
            d = d.minusDays(1)
            if (!isHoliday(d)) return d
        }
        // 兜底:回溯 7 天都还是 HOLIDAY,返回最后一次结果
        return d
    }

    /**
     * 向后找最近的非 HOLIDAY 日（最多前进 7 天保护性能）。
     */
    private suspend fun findNextWorkday(date: LocalDate): LocalDate {
        var d = date
        for (i in 1..7) {
            d = d.plusDays(1)
            if (!isHoliday(d)) return d
        }
        return d
    }
}