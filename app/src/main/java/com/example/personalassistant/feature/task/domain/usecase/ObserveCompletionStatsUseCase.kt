package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.CompletionCalculator
import com.example.personalassistant.feature.task.domain.model.CompletionStats
import com.example.personalassistant.feature.task.domain.model.DailyCompletion
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Phase 22.6：完成率统计
 *
 * 给定统计窗口（默认最近 7 天），算出：
 * - 完成率 = 实际完成 ÷ 原计划
 * - 拖延数 = 逾期仍未完成的数量
 * - 连续完成天数（跨更长的时间窗看，见 [streakWindowDays]）
 */
open class ObserveCompletionStatsUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    /**
     * @param days 统计窗口（天）。默认 7 天 ≈ 一周。
     * @param today 今天（便于单测注入固定日期）
     */
    open suspend operator fun invoke(
        days: Int = DEFAULT_WINDOW_DAYS,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): CompletionStats {
        val windowEnd = endOfDayMillis(today, zoneId)
        val windowStart = startOfDayMillis(today.minusDays((days - 1).toLong()), zoneId)

        // 窗口内的每日数据 → 完成率 / 拖延数
        val inWindow = taskRepository.getDailyCompletion(windowStart, windowEnd)
            .map { it.toDailyCompletion(zoneId) }

        // 连续天数要跨更长的窗口（否则连续 10 天只能看到 7 天）
        val streakStart = startOfDayMillis(today.minusDays((streakWindowDays - 1).toLong()), zoneId)
        val forStreak = taskRepository.getDailyCompletionUntil(streakStart, windowEnd)
            .map { it.toDailyCompletion(zoneId) }

        val windowStats = CompletionCalculator.stats(inWindow, today)
        val streak = CompletionCalculator.streak(forStreak, today)

        return windowStats.copy(streakDays = streak)
    }

    companion object {
        /** 默认统计窗口：7 天（一周） */
        const val DEFAULT_WINDOW_DAYS = 7

        /** 连续天数的回看窗口：365 天 */
        const val streakWindowDays = 365
    }
}

/** Room 行 → domain 模型 */
private fun com.example.personalassistant.feature.task.data.local.DailyCompletionRow.toDailyCompletion(
    zoneId: ZoneId,
): DailyCompletion = DailyCompletion(
    date = Instant.ofEpochMilli(dayStart).atZone(zoneId).toLocalDate(),
    planned = planned,
    completed = completed,
)

/** 某天 00:00 的毫秒时间戳 */
private fun startOfDayMillis(date: LocalDate, zoneId: ZoneId): Long =
    date.atStartOfDay(zoneId).toInstant().toEpochMilli()

/** 某天 23:59:59.999 的毫秒时间戳 */
private fun endOfDayMillis(date: LocalDate, zoneId: ZoneId): Long =
    date.atTime(23, 59, 59, 999_000_000).atZone(zoneId).toInstant().toEpochMilli()
