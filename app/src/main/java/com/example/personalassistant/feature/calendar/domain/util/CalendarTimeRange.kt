package com.example.personalassistant.feature.calendar.domain.util

import java.time.Instant
import java.time.ZoneId

/**
 * Phase 20.7：跨午夜时段的切段工具
 *
 * ## 为什么要有它
 *
 * [com.example.personalassistant.feature.calendar.domain.util.EventTimeMapper] **不处理跨午夜**
 * （它只把 Event 转成「星期几 + 当天分钟数」，跨天的时段在它眼里就是一段脏数据）。
 *
 * 但「23:00 → 次日 01:00」这种时段在日历里是真实存在的需求：
 * 用户在晚上安排了一个跨夜的清单项，日历上不能把它**截断**成 1 小时，
 * 也不能让它凭空消失 —— 应该画成一条**跨天条**：
 * - 当天那半截：从 23:00 一直画到当天结束（24:00），标上「跨天」
 * - 次日那半截：从 00:00 画到 01:00，同样标上「跨天」
 *
 * ## 切段规则
 *
 * - 结束时间 ≤ 开始时间（脏数据）→ 按 **1 小时**处理（与既有 [TimeBlockLayout] 的口径一致）
 * - 结束时间落在次日 → 切成两段：前段到 24:00，后段从 00:00
 * - 跨越多天 → 每天一段，中间几天整段占满
 *
 * ## 时区
 *
 * 用 [ZoneId.systemDefault]（与 CalendarRepositoryImpl.dateToDayOfWeek 对齐），
 * 避免跨时区用户算错当天。
 */
object CalendarTimeRange {

    /** 一天的分钟数（24:00） */
    const val MINUTES_PER_DAY = 24 * 60

    /** 一个时段落在某一天上的那一截 */
    data class Segment(
        /** 该截所在那天的 00:00 时间戳 */
        val dayStart: Long,
        /** 该截的开始分钟数（0..1440） */
        val startMinute: Int,
        /** 该截的结束分钟数（0..1440） */
        val endMinute: Int,
        /**
         * 这个时段**跨天**了。
         *
         * 跨天的时段会被切成多段，**每一段**都带这个标记 ——
         * 这样 UI 上不管画到头段还是尾段，都能标出「跨天」。
         */
        val crossesMidnight: Boolean,
    )

    /** 任意时刻 → 当天 00:00 的时间戳 */
    fun startOfDay(millis: Long): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /**
     * 把一段时段切成「每天一段」
     *
     * @param startMillis 开始时间（epoch 毫秒）
     * @param endMillis 结束时间（epoch 毫秒）；≤ 开始时间时按 1 小时处理
     * @return 按天升序的切片；正常情况下 1 段，跨午夜 2 段，跨多天则更多
     */
    fun segments(startMillis: Long, endMillis: Long): List<Segment> {
        val zone = ZoneId.systemDefault()
        val end = if (endMillis <= startMillis) startMillis + 60_000L else endMillis

        var day = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        // 「跨天」的判据是**这段时长落在两个不同的自然日**，
        // 不是「结束时间 > 开始时间」——
        // 后者会把「没有结束时间、按 1 小时补齐」的普通时段也标成跨天（错的）。
        val crossMidnight = lastDay.isAfter(day)

        val result = mutableListOf<Segment>()
        // 防御：脏数据（跨度几个月）最多切 32 段，别把内存撑爆
        var guard = 0
        while (!day.isAfter(lastDay) && guard < MAX_SEGMENTS) {
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val nextDayStart = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segStart = maxOf(startMillis, dayStart)
            val segEnd = minOf(end, nextDayStart)
            val startMinute = ((segStart - dayStart) / 60_000L).toInt()
                .coerceIn(0, MINUTES_PER_DAY)
            val endMinute = ((segEnd - dayStart) / 60_000L).toInt()
                .coerceIn(startMinute, MINUTES_PER_DAY)
            result += Segment(
                dayStart = dayStart,
                startMinute = startMinute,
                endMinute = endMinute,
                crossesMidnight = crossMidnight,
            )
            day = day.plusDays(1)
            guard++
        }
        return result
    }

    /**
     * 取时段落在指定那一天上的那一截
     *
     * @param dayStart 那一天的 00:00 时间戳（夏令时切换日可能不是整点，会先归一化）
     * @return null = 这个时段不落在那一天（不是那天的内容）
     */
    fun segmentOn(startMillis: Long, endMillis: Long, dayStart: Long): Segment? {
        val zone = ZoneId.systemDefault()
        // 归一化到那天的本地零点 —— 免得夏令时切换日（午夜不是整点）对不上
        val target = Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return segments(startMillis, endMillis).firstOrNull { it.dayStart == target }
    }

    private const val MAX_SEGMENTS = 32
}
