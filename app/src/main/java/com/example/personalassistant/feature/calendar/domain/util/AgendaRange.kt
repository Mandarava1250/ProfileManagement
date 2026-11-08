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

        AgendaScope.WORKDAY, AgendaScope.RESTDAY -> {
            // 周起始是周一（与 CalendarViewModel.computeWeekRange 一致）
            val weekStart = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
            val offsets = if (scope == AgendaScope.WORKDAY) 0L..4L else 5L..6L
            offsets.map { weekStart.plusDays(it).startOfDayMillis(zone) }
        }
    }
}

/** 本地日期当天 00:00 的时间戳 */
private fun LocalDate.startOfDayMillis(zone: ZoneId): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()
