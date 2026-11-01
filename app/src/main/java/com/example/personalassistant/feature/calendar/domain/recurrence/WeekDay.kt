package com.example.personalassistant.feature.calendar.domain.recurrence

import java.time.DayOfWeek

/**
 * Phase 8 Sub-step 9 Step 3：星期几（RFC 5545 §3.3.10 BYDAY 关键字的子集）
 *
 * - [rfcShort]：RFC 5545 两字母缩写（如 "MO"=周一）
 * - [dayOfWeek]：映射到 [java.time.DayOfWeek]
 *
 * 涟宝备注：RFC 5545 支持 "1MO" / "-1FR" 这种「周内第几个星期几」的形式
 * （"1MO"=本月第一个周一）。这里只解析「纯星期几」形式（"MO"/"TU"...），
 * 序号形式做兼容读取但本项目用不到。
 */
enum class WeekDay(val rfcShort: String, val dayOfWeek: DayOfWeek) {
    MONDAY("MO", DayOfWeek.MONDAY),
    TUESDAY("TU", DayOfWeek.TUESDAY),
    WEDNESDAY("WE", DayOfWeek.WEDNESDAY),
    THURSDAY("TH", DayOfWeek.THURSDAY),
    FRIDAY("FR", DayOfWeek.FRIDAY),
    SATURDAY("SA", DayOfWeek.SATURDAY),
    SUNDAY("SU", DayOfWeek.SUNDAY);

    companion object {
        private val byShort: Map<String, WeekDay> = values().associateBy { it.rfcShort }

        /** 从 RFC 缩写解析（不区分大小写）；未知返回 null */
        fun fromRfcShort(short: String): WeekDay? = byShort[short.uppercase()]
    }
}