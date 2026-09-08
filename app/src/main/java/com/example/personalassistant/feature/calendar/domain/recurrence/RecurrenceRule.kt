package com.example.personalassistant.feature.calendar.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Phase 8 Sub-step 9 Step 3：RRULE 重复规则（RFC 5545 §3.3.10 子集）
 *
 * ## 字段语义
 * - [freq]：频率（必填）
 * - [interval]：步长（默认 1）。FREQ=DAILY + INTERVAL=2 表示「每两天」
 * - [count]：总共发生次数（与 [until] 互斥；同时存在时优先 count）
 * - [until]：结束日期（包含当天）。与 count 互斥
 * - [byDay]：WEEKLY 专用，限定具体星期几（空集合 = 该周的所有日）
 * - [byMonthDay]：MONTHLY 专用，限定日期（1..31，支持负数表示倒数）
 * - [byMonth]：YEARLY 专用，限定月份（1..12）
 * - [bySetPos]：从 RFC 5545 §3.3.10 取子集（如「倒数第一个星期五」）
 *   本项目只在 YEARLY × byDay 场景下用，其他场景解析保留但不展开
 * - [wkst]：周开始日（默认 MONDAY，符合 ISO 8601）
 *
 * ## 不支持的字段（解析时丢弃，不报错）
 * - BYSECOND / BYMINUTE / BYHOUR / BYWEEKNO / BYHOUR（细粒度不适用）
 * - EXDATE / RDATE：排除/附加日期集合，本项目暂不实现
 *
 * ## 存储方式
 * - DB 存 RFC 5545 字符串（如 "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE"）
 * - UI 层直接读写 [RecurrenceRule] 对象，提交时 [toRfcString] 转换
 *
 * ## 与 Event 关联
 * Event 通过 `recurrence: RecurrenceRule?` 字段持有（null = 不重复）。
 * 事件的主表 `date` 字段记录**首次发生日期**（DTSTART），后续 occurrence
 * 由 [RecurrenceExpander] 按规则展开。
 */
data class RecurrenceRule(
    val freq: RecurrenceFreq,
    val interval: Int = 1,
    val count: Int? = null,
    val until: LocalDate? = null,
    val byDay: Set<WeekDay> = emptySet(),
    val byMonthDay: Set<Int> = emptySet(),
    val byMonth: Set<Int> = emptySet(),
    val bySetPos: Set<Int> = emptySet(),
    val wkst: WeekDay = WeekDay.MONDAY,
) {
    init {
        require(interval >= 1) { "INTERVAL 必须 >= 1（实际 $interval）" }
        require(count == null || count >= 1) { "COUNT 必须 >= 1（实际 $count）" }
        require(count == null || until == null) { "COUNT 与 UNTIL 互斥" }
        byDay.forEach { d -> require(d in WeekDay.values().toSet()) }
        byMonthDay.forEach { d -> require(d != 0 && d in -31..31) { "BYMONTHDAY 不能为 0 或超出 ±31（实际 $d）" } }
        byMonth.forEach { m -> require(m in 1..12) { "BYMONTH 必须在 1..12（实际 $m）" } }
        bySetPos.forEach { p -> require(p != 0 && p in -366..366) { "BYSETPOS 不能为 0 或超出 ±366（实际 $p）" } }
    }

    /** 序列化为 RFC 5545 字符串（例：FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE） */
    fun toRfcString(): String {
        val parts = mutableListOf("FREQ=${freq.rfcName}")
        if (interval != 1) parts.add("INTERVAL=$interval")
        if (count != null) parts.add("COUNT=$count")
        if (until != null) parts.add("UNTIL=${formatUntil(until)}")
        if (byDay.isNotEmpty()) parts.add("BYDAY=" + byDay.sortedBy { it.ordinal }.joinToString(",") { it.rfcShort })
        if (byMonthDay.isNotEmpty()) parts.add("BYMONTHDAY=" + byMonthDay.sorted().joinToString(","))
        if (byMonth.isNotEmpty()) parts.add("BYMONTH=" + byMonth.sorted().joinToString(","))
        if (bySetPos.isNotEmpty()) parts.add("BYSETPOS=" + bySetPos.sorted().joinToString(","))
        if (wkst != WeekDay.MONDAY) parts.add("WKST=${wkst.rfcShort}")
        return parts.joinToString(";")
    }

    /** 人类可读描述（UI 提示） */
    fun describe(): String {
        val every = if (interval == 1) "" else "每 $interval "
        val suffix = when (freq) {
            RecurrenceFreq.DAILY -> "天"
            RecurrenceFreq.WEEKLY -> "周"
            RecurrenceFreq.MONTHLY -> "月"
            RecurrenceFreq.YEARLY -> "年"
        }
        val base = "${every}${freq.rfcName.lowercase().replaceFirstChar { it.uppercase() }}$suffix".trim()
        val days = if (byDay.isNotEmpty()) {
            "（" + byDay.sortedBy { it.ordinal }.joinToString("、") { it.dayOfWeek.toChinese() } + "）"
        } else ""
        val dates = if (byMonthDay.isNotEmpty()) "（${byMonthDay.sorted().joinToString("、")} 日）" else ""
        val months = if (byMonth.isNotEmpty()) "（${byMonth.sorted().joinToString("、")} 月）" else ""
        val end = when {
            count != null -> "，共 $count 次"
            until != null -> "，直到 ${until}"
            else -> ""
        }
        return base + days + dates + months + end
    }

    companion object {
        /**
         * 从 RFC 5545 字符串解析（容错：忽略不认识的字段）
         *
         * @return 解析结果；空字符串或无效字符串返回 null
         */
        fun fromRfcString(rrule: String): RecurrenceRule? {
            val trimmed = rrule.trim()
            if (trimmed.isEmpty()) return null
            val parts = trimmed.split(";")
                .mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx <= 0) null else part.substring(0, idx).trim().uppercase() to
                        part.substring(idx + 1).trim()
                }
            if (parts.isEmpty()) return null

            val freq = parts.firstOrNull { it.first == "FREQ" }?.second
                ?.let { RecurrenceFreq.values().firstOrNull { f -> f.rfcName == it.uppercase() } }
                ?: return null

            val interval = parts.firstOrNull { it.first == "INTERVAL" }?.second?.toIntOrNull() ?: 1
            val count = parts.firstOrNull { it.first == "COUNT" }?.second?.toIntOrNull()
            val until = parts.firstOrNull { it.first == "UNTIL" }?.second?.let(::parseUntil)
            val byDay = parts.firstOrNull { it.first == "BYDAY" }?.second
                ?.split(",")
                ?.mapNotNull { token ->
                    // 支持 "MO" / "1MO" / "-1MO" 三种；只取末尾两个字符
                    val short = if (token.length >= 2) token.substring(token.length - 2) else token
                    WeekDay.fromRfcShort(short)
                }
                ?.toSet()
                ?: emptySet()
            val byMonthDay = parts.firstOrNull { it.first == "BYMONTHDAY" }?.second
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.toSet()
                ?: emptySet()
            val byMonth = parts.firstOrNull { it.first == "BYMONTH" }?.second
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.toSet()
                ?: emptySet()
            val bySetPos = parts.firstOrNull { it.first == "BYSETPOS" }?.second
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.toSet()
                ?: emptySet()
            val wkst = parts.firstOrNull { it.first == "WKST" }?.second
                ?.let { WeekDay.fromRfcShort(it) }
                ?: WeekDay.MONDAY

            return runCatching {
                RecurrenceRule(
                    freq = freq,
                    interval = interval,
                    count = count,
                    until = until,
                    byDay = byDay,
                    byMonthDay = byMonthDay,
                    byMonth = byMonth,
                    bySetPos = bySetPos,
                    wkst = wkst,
                )
            }.getOrNull()
        }

        /** RFC 5545 §3.3.5：UTC 时间（Z 结尾）或本地日期 */
        private fun parseUntil(s: String): LocalDate? {
            return runCatching {
                // YYYYMMDDTHHMMSSZ 或 YYYYMMDD
                val cleaned = s.removeSuffix("Z")
                if (cleaned.length == 8) {
                    LocalDate.of(
                        cleaned.substring(0, 4).toInt(),
                        cleaned.substring(4, 6).toInt(),
                        cleaned.substring(6, 8).toInt(),
                    )
                } else if (cleaned.length >= 8) {
                    LocalDate.of(
                        cleaned.substring(0, 4).toInt(),
                        cleaned.substring(4, 6).toInt(),
                        cleaned.substring(6, 8).toInt(),
                    )
                } else null
            }.getOrNull()
        }

        private fun formatUntil(d: LocalDate): String =
            "%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth)

        // ===== 工厂方法 =====

        fun daily(interval: Int = 1) = RecurrenceRule(freq = RecurrenceFreq.DAILY, interval = interval)
        fun weekly(interval: Int = 1, days: Set<WeekDay> = emptySet()) =
            RecurrenceRule(freq = RecurrenceFreq.WEEKLY, interval = interval, byDay = days)
        fun monthly(interval: Int = 1, byMonthDay: Set<Int> = emptySet()) =
            RecurrenceRule(freq = RecurrenceFreq.MONTHLY, interval = interval, byMonthDay = byMonthDay)
        fun yearly(interval: Int = 1, byMonth: Set<Int> = emptySet(), byDay: Set<WeekDay> = emptySet()) =
            RecurrenceRule(freq = RecurrenceFreq.YEARLY, interval = interval, byMonth = byMonth, byDay = byDay)
    }
}

/** DayOfWeek 中文标签（用于 describe 文案） */
internal fun DayOfWeek.toChinese(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
    else -> name
}