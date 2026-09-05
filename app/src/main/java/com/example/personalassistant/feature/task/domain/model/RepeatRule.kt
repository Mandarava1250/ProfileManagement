package com.example.personalassistant.feature.task.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 任务重复规则。
 *
 * - [None] — 不重复
 * - [Daily] — 每天
 * - [Weekly] — 每周同一天（沿用 dueDate 的 weekday）
 * - [Monthly] — 每月同一日（沿用 dueDate 的 dayOfMonth）
 * - [Yearly] — 每年同一月同一日
 * - [CustomWeekdays] — 自定义一周内多天，例如 [MONDAY, WEDNESDAY, FRIDAY]
 *
 * 每种规则都实现 [nextOccurrence]：给定当前 dueDate，返回下一次应当出现的日期。
 * 由于月度/年度规则可能落到 2 月 30 日这种不存在日期，约定取月末兜底。
 */
sealed class RepeatRule {
    abstract fun nextOccurrence(current: LocalDate): LocalDate?

    object None : RepeatRule() {
        override fun nextOccurrence(current: LocalDate): LocalDate? = null
    }

    object Daily : RepeatRule() {
        override fun nextOccurrence(current: LocalDate): LocalDate = current.plusDays(1)
    }

    object Weekly : RepeatRule() {
        override fun nextOccurrence(current: LocalDate): LocalDate = current.plusWeeks(1)
    }

    object Monthly : RepeatRule() {
        override fun nextOccurrence(current: LocalDate): LocalDate =
            current.plusMonths(1).withDayOfMonth(
                minOf(current.dayOfMonth, current.plusMonths(1).lengthOfMonth())
            )
    }

    object Yearly : RepeatRule() {
        override fun nextOccurrence(current: LocalDate): LocalDate = current.plusYears(1)
    }

    /**
     * 自定义星期几集合。空集合视为 None（防御性，正常 UI 不会传空）。
     * 输入顺序无关紧要；返回的下一次出现按 DayOfWeek 自然顺序。
     */
    data class CustomWeekdays(val days: Set<DayOfWeek>) : RepeatRule() {
        init {
            require(days.isNotEmpty()) { "CustomWeekdays 必须至少包含一个 weekday" }
        }

        override fun nextOccurrence(current: LocalDate): LocalDate? {
            // 1..7 天内找下一个匹配的 weekday
            for (offset in 1..7) {
                val candidate = current.plusDays(offset.toLong())
                if (candidate.dayOfWeek in days) return candidate
            }
            return null // 理论上不会到达（任何 7 天序列必然覆盖周一到周日）
        }

        companion object {
            /** 周一到周五（工作日） */
            val WEEKDAYS: Set<DayOfWeek> = setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
            )

            /** 周末 */
            val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
    }

    companion object {
        /**
         * 从 RepeatType enum + 可选 customWeekdays 推断 RepeatRule。
         *
         * Phase 4a 约定：
         * - DB 字段 repeatType 只存 5 种基础值（NONE/DAILY/WEEKLY/MONTHLY/YEARLY）
         * - CustomWeekdays 暂时不持久化（避免改 schema），UI 层可临时构造但不写回 DB
         */
        fun fromRepeatType(
            type: RepeatType,
            customDays: Set<DayOfWeek>? = null,
        ): RepeatRule = when (type) {
            RepeatType.NONE -> None
            RepeatType.DAILY -> Daily
            RepeatType.WEEKLY -> Weekly
            RepeatType.MONTHLY -> Monthly
            RepeatType.YEARLY -> Yearly
        }.let { base ->
            // 仅在 customDays 明确提供且非空时提升为 CustomWeekdays
            if (!customDays.isNullOrEmpty() && base == Daily) {
                CustomWeekdays(customDays)
            } else base
        }

        /**
         * RepeatRule → (RepeatType, customWeekdays?)。
         *
         * CustomWeekdays 在 Phase 4a 退化保存为 DAILY（customDays 丢失），
         * 留待 Phase 4b 升级 schema。
         */
        fun toRepeatType(rule: RepeatRule): Pair<RepeatType, Set<DayOfWeek>?> = when (rule) {
            None -> RepeatType.NONE to null
            Daily -> RepeatType.DAILY to null
            Weekly -> RepeatType.WEEKLY to null
            Monthly -> RepeatType.MONTHLY to null
            Yearly -> RepeatType.YEARLY to null
            is CustomWeekdays -> RepeatType.DAILY to rule.days  // 退化保存
        }

        /** DB 存的字符串 ↔ 对象的转换辅助（Phase 4a 不走 DB 转换，留作 Phase 5 持久化时用） */
        fun fromStorage(value: String?): RepeatRule = when (value) {
            null, "" -> None
            "DAILY" -> Daily
            "WEEKLY" -> Weekly
            "MONTHLY" -> Monthly
            "YEARLY" -> Yearly
            else -> {
                // "CUSTOM:MONDAY,WEDNESDAY" 这种格式
                if (value.startsWith("CUSTOM:")) {
                    val parts = value.removePrefix("CUSTOM:").split(",")
                    val days = parts.mapNotNull { name ->
                        runCatching { DayOfWeek.valueOf(name.trim()) }.getOrNull()
                    }.toSet()
                    if (days.isEmpty()) None else CustomWeekdays(days)
                } else None
            }
        }
    }
}
