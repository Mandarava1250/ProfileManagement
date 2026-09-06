package com.example.personalassistant.feature.finance.presentation.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Phase 7a：finance 模块日期工具
 *
 * - 流水列表：按日期分组显示 "2026-09-06 / 09-05 / 昨天"
 * - 月度报表：yyyy-MM
 */
object FinanceDateUtils {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * 把任意毫秒时间戳格式化成 "yyyy-MM-dd"
     * （DB 里 date 字段就是业务时间戳，可直接展示）
     */
    fun formatDate(millis: Long): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * 显示相对日期：
     * - 今天 → "今天"
     * - 昨天 → "昨天"
     * - 本年 → "MM-dd"
     * - 其它 → "yyyy-MM-dd"
     */
    fun formatRelativeDate(millis: Long, today: LocalDate = LocalDate.now(zone)): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return when {
            date == today -> "今天"
            date == today.minusDays(1) -> "昨天"
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("MM-dd"))
            else -> date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }

    /**
     * 把任意毫秒时间戳格式化成 "HH:mm"
     */
    fun formatTimeOfDay(millis: Long): String {
        val date = Date(millis)
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    /** 当前月份 yyyy-MM（用于按月查报表） */
    fun currentYearMonth(): String =
        java.time.YearMonth.now(zone).toString()

    /** 业务时间戳归到当天 00:00 的毫秒数（用于按日分组 / 月份区间起算） */
    fun startOfDayMillis(millis: Long): Long {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
