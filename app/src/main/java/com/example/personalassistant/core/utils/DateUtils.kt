package com.example.personalassistant.core.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 时间工具类
 *
 * 设计要点（说明书 6.1）：
 * - 时区问题：所有存储都是 UTC 毫秒时间戳
 * - 课表时间：startTime/endTime 存当天分钟数（0-1439）
 * - 周起始日：默认周一为第一天
 * - 零钱池日期：当天 00:00 的时间戳
 */
object DateUtils {

    /** 默认周起始日（周一=1）*/
    const val WEEK_START_DAY_OF_WEEK = java.time.DayOfWeek.MONDAY

    private val zoneId: ZoneId get() = ZoneId.systemDefault()

    // ============== 当天/本周/本月 ==============

    /** 今天 00:00 的时间戳 */
    fun todayStartMillis(): Long =
        LocalDate.now().atStartOfDay(zoneId).toInstant().toEpochMilli()

    /** 今天 23:59:59 的时间戳 */
    fun todayEndMillis(): Long =
        LocalDate.now().atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()

    /** 本周一 00:00 的时间戳 */
    fun weekStartMillis(): Long =
        LocalDate.now().with(WEEK_START_DAY_OF_WEEK).atStartOfDay(zoneId).toInstant().toEpochMilli()

    /** 本周日 23:59:59 的时间戳 */
    fun weekEndMillis(): Long =
        LocalDate.now().with(WEEK_START_DAY_OF_WEEK).plusDays(6)
            .atTime(LocalTime.MAX).atZone(zoneId).toInstant().toEpochMilli()

    /** 本月第一天 00:00 的时间戳 */
    fun monthStartMillis(yearMonth: String? = null): Long {
        val ym = yearMonth ?: currentYearMonth()
        val (y, m) = parseYearMonth(ym)
        return LocalDate.of(y, m, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /** 下个月第一天 00:00 的时间戳（即本月结束边界） */
    fun monthEndMillis(yearMonth: String? = null): Long {
        val ym = yearMonth ?: currentYearMonth()
        val (y, m) = parseYearMonth(ym)
        return LocalDate.of(y, m, 1).plusMonths(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    // ============== 格式化 ==============

    private val FORMATTER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
    private val FORMATTER_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val FORMATTER_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val FORMATTER_YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM", Locale.CHINA)
    private val FORMATTER_CHINESE_DATE = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    private val FORMATTER_CHINESE_DAY = DateTimeFormatter.ofPattern("d日", Locale.CHINA)

    fun formatDate(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate().format(FORMATTER_DATE)

    fun formatTime(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalTime().format(FORMATTER_TIME)

    fun formatDateTime(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDateTime().format(FORMATTER_DATE_TIME)

    fun formatChineseDate(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate().format(FORMATTER_CHINESE_DATE)

    fun formatChineseDay(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate().format(FORMATTER_CHINESE_DAY)

    fun currentYearMonth(): String = LocalDate.now().format(FORMATTER_YEAR_MONTH)

    // ============== 分钟数 ↔ 时间字符串 ==============

    /** 把分钟数（0-1439）格式化为 "HH:mm" */
    fun formatMinutesOfDay(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(Locale.CHINA, "%02d:%02d", h, m)
    }

    /** 把 "HH:mm" 解析为分钟数（0-1439）*/
    fun parseMinutesOfDay(text: String): Int? = try {
        val parts = text.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        if (h !in 0..23 || m !in 0..59) null else h * 60 + m
    } catch (e: Exception) {
        null
    }

    // ============== 周次计算 ==============

    /** 当前学期第几周（1-based） */
    fun currentWeekNumber(semesterStartMillis: Long): Int {
        val semesterStart = Instant.ofEpochMilli(semesterStartMillis).atZone(zoneId).toLocalDate()
        val today = LocalDate.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(semesterStart, today)
        return (days / 7).toInt() + 1
    }

    /** 解析 "yyyy-MM" 为 (year, month) */
    fun parseYearMonth(ym: String): Pair<Int, Int> {
        val parts = ym.split("-")
        return parts[0].toInt() to parts[1].toInt()
    }

    /** 计算某月有多少天 */
    fun daysInMonth(year: Int, month: Int): Int =
        java.time.YearMonth.of(year, month).lengthOfMonth()

    // ============== 友好显示 ==============

    /** 距离现在的友好时间描述（"刚刚"/"5 分钟前"/"昨天"/"3 天前"） */
    fun friendlyTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3600_000} 小时前"
            diff < 7 * 86_400_000 -> "${diff / 86_400_000} 天前"
            else -> formatDate(timestamp)
        }
    }
}
