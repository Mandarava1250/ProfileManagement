package com.example.personalassistant.feature.calendar.domain.util

import com.example.personalassistant.feature.calendar.domain.model.AgendaScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日程流要聚合哪几天。
 *
 * @param anchorMillis 锚点日期（当天任意时刻，取其本地日期）
 * @param scope 聚合范围
 * @return 每天 00:00 的时间戳，按时间升序
 */
fun agendaDates(
    anchorMillis: Long,
    scope: AgendaScope,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Long> {
    val anchor = Instant.ofEpochMilli(anchorMillis).atZone(zone).toLocalDate()
    return when (scope) {
        // 日日程：只这一天，不展开成整周
        AgendaScope.DAY -> listOf(anchor.startOfDayMillis(zone))

        AgendaScope.WORKDAY, AgendaScope.RESTDAY, AgendaScope.WEEK -> {
            // 周起始是周一（与 CalendarViewModel.computeWeekRange 一致）
            val weekStart = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
            val offsets = when (scope) {
                AgendaScope.WORKDAY -> 0L..4L
                AgendaScope.RESTDAY -> 5L..6L
                // 整周：周一 ~ 周日，七天连着排
                AgendaScope.WEEK -> 0L..6L
                AgendaScope.DAY -> 0L..0L
            }
            offsets.map { weekStart.plusDays(it).startOfDayMillis(zone) }
        }
    }
}

/** 本地日期当天 00:00 的时间戳 */
private fun LocalDate.startOfDayMillis(zone: ZoneId): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()
