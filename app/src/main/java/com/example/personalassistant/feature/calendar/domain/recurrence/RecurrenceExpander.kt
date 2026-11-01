package com.example.personalassistant.feature.calendar.domain.recurrence

import java.time.LocalDate

/**
 * Phase 8 Sub-step 9 Step 3：把 [RecurrenceRule] 展开成「具体发生日期」的结果
 *
 * 涟宝备注：[date] 是发生日期，[isRecurring] 标记该 occurrence 是规则展开出来的（true）
 * 还是首次发生（false），方便后续 UI 区分「原事件」与「重复 occurrence」。
 */
data class EventOccurrence(
    val date: LocalDate,
    val isRecurring: Boolean,
)

/**
 * Phase 8 Sub-step 9 Step 3：RRULE 展开器（核心算法）
 *
 * ## 用法
 * ```
 * val rule = RecurrenceRule.fromRfcString("FREQ=WEEKLY;BYDAY=MO,WE")
 * val firstDate = LocalDate.of(2026, 9, 7) // 首次发生
 * val occurrences = RecurrenceExpander.expand(firstDate, rule, LocalDate.of(2026, 12, 31))
 * ```
 *
 * ## 设计取舍
 * - **不展开到 [LocalDate.now()]，而是 [rangeEnd]**：让调用方控制时间窗，避免无界展开
 * - **BYxxx 优先于自然步进**：RFC 5545 标准是先生成 set 再按 BYxxx 过滤，
 *   本实现采用「步进到下一个候选 → 检查 BYxxx 匹配」的反向策略，
 *   正确性等价、性能更优（不用预先生成全集）
 * - **byMonthDay=-1 → 当月最后一天**：用 [LocalDate.lengthOfMonth] 计算
 * - **bySetPos × byDay**：支持「第 3 个周一」「倒数第 1 个周五」等
 *
 * ## 算法
 * 1. cursor = start
 * 2. 如果 cursor.matches(rule) → add cursor；count++（first 不计数）
 * 3. 根据 freq + interval 推进 cursor 到下一个候选
 * 4. cursor > rangeEnd 或 count 达到 limit → break
 * 5. 处理 byMonth / byMonthDay / byDay / bySetPos 的边界情况
 */
object RecurrenceExpander {

    private const val MAX_OCCURRENCES = 366  // Safety upper bound to prevent runaway loops

    fun expand(
        start: LocalDate,
        rule: RecurrenceRule,
        rangeEnd: LocalDate,
    ): List<LocalDate> {
        if (rangeEnd.isBefore(start)) return emptyList()

        val results = mutableListOf<LocalDate>()
        val maxCount = rule.count ?: MAX_OCCURRENCES
        if (rule.until != null && start.isAfter(rule.until)) return emptyList()

        // 「纯 FREQ」：没有任何 BYxxx。间隔语义需被严格遵守。
        // 「混合 BYxxx」：BYxxx 才是主要约束，间隔仅用于跨窗口。
        val isPure = when (rule.freq) {
            RecurrenceFreq.DAILY -> rule.byMonth.isEmpty() && rule.byMonthDay.isEmpty()
                    && rule.byDay.isEmpty() && rule.bySetPos.isEmpty()
            RecurrenceFreq.WEEKLY -> rule.byDay.isEmpty()
            RecurrenceFreq.MONTHLY -> rule.byMonthDay.isEmpty()
            RecurrenceFreq.YEARLY -> rule.byMonth.isEmpty() && rule.byMonthDay.isEmpty()
                    && rule.bySetPos.isEmpty()
        }

        // 1. 首日先检查（first occurrence 强制 add）
        if (matchesRule(start, rule)) {
            results.add(start)
        }

        // 2. 推进 cursor
        var cursor = nextCandidate(start, rule)
        var lastMatched = if (results.isEmpty()) null else results.last()
        var safetyCounter = 0

        while (!cursor.isAfter(rangeEnd) && results.size < maxCount && safetyCounter < 10_000) {
            safetyCounter++
            if (rule.until != null && cursor.isAfter(rule.until)) break

            val nextFloor = lastMatched?.let { intervalFloor(it, rule) }
            val isBeforeFloor = nextFloor != null && cursor.isBefore(nextFloor)

            if (isBeforeFloor && isPure) {
                // 纯 FREQ：跳过间隔内的所有日期
                cursor = nextFloor!!
                continue
            }

            if (matchesRule(cursor, rule)) {
                results.add(cursor)
                lastMatched = cursor
                if (results.size >= maxCount) break
            }

            cursor = cursor.plusDays(1)
        }

        return results
    }

    /**
     * 计算「下一间隔的起始日」—— lastMatched 加上 interval * freq_unit。
     *
     * 举例：WEEKLY interval=2, lastMatched=周五 → 下间隔起点 = 再下周五 (即 lastMatched.plusWeeks(2))。
     */
    private fun intervalFloor(lastMatched: LocalDate, rule: RecurrenceRule): LocalDate {
        return when (rule.freq) {
            RecurrenceFreq.DAILY -> lastMatched.plusDays(rule.interval.toLong())
            RecurrenceFreq.WEEKLY -> lastMatched.plusWeeks(rule.interval.toLong())
            RecurrenceFreq.MONTHLY -> lastMatched.plusMonths(rule.interval.toLong())
            RecurrenceFreq.YEARLY -> lastMatched.plusYears(rule.interval.toLong())
        }
    }

    /**
     * 把 [EventOccurrence] 列表（带首次 + 重复）输出：
     * - 首次（first）标记为 isRecurring=false
     * - 其余标记为 isRecurring=true
     */
    fun expandOccurrences(
        start: LocalDate,
        rule: RecurrenceRule,
        rangeEnd: LocalDate,
    ): List<EventOccurrence> {
        val dates = expand(start, rule, rangeEnd)
        return dates.mapIndexed { index, date ->
            EventOccurrence(date = date, isRecurring = index > 0)
        }
    }

    /**
     * 检查 [date] 是否匹配 RRULE 的 BYxxx 约束
     *
     * 算法：
     * 1. 检查 byMonth（如果存在）
     * 2. 检查 byMonthDay（如果存在）
     * 3. 检查 byDay（如果存在）
     * 4. 检查 bySetPos（如果存在，与 byDay/bymonthday 共同决定）
     * 5. 若以上 BYxxx 都为空 → 默认匹配
     */
    internal fun matchesRule(date: LocalDate, rule: RecurrenceRule): Boolean {
        // byMonth 优先
        if (rule.byMonth.isNotEmpty() && date.monthValue !in rule.byMonth) return false

        // byMonthDay：把负数转成正数（-1 → lengthOfMonth）
        if (rule.byMonthDay.isNotEmpty()) {
            val lastDay = date.lengthOfMonth()
            val normalized = rule.byMonthDay.map { if (it < 0) lastDay + 1 + it else it }
            if (date.dayOfMonth !in normalized) return false
        }

        // byDay：必须命中至少一个 weekday
        if (rule.byDay.isNotEmpty()) {
            val dayMatch = rule.byDay.any { it.dayOfWeek == date.dayOfWeek }
            if (!dayMatch) return false
        }

        // bySetPos：需要先收集「同 month 内同类日」再取第 N 个
        if (rule.bySetPos.isNotEmpty()) {
            if (!matchesBySetPos(date, rule)) return false
        }

        return true
    }

    /**
     * 计算「下一个候选日期」—— 按 freq + interval 步进
     *
     * 注意：这里的候选未必最终命中（BYxxx 可能过滤掉），但保证不会遗漏
     */
    internal fun nextCandidate(current: LocalDate, rule: RecurrenceRule): LocalDate {
        return when (rule.freq) {
            RecurrenceFreq.DAILY -> current.plusDays(rule.interval.toLong())
            RecurrenceFreq.WEEKLY -> {
                // 如果有 byDay 限定，下一个候选就是同周内下一个匹配的 weekday
                if (rule.byDay.isNotEmpty()) {
                    current.plusDays(1) // 1 天 1 天推进，确保 BYxxx 不漏
                } else {
                    current.plusWeeks(rule.interval.toLong())
                }
            }
            RecurrenceFreq.MONTHLY -> {
                if (rule.byMonthDay.isNotEmpty()) {
                    current.plusDays(1)
                } else {
                    current.plusMonths(rule.interval.toLong())
                }
            }
            RecurrenceFreq.YEARLY -> {
                if (rule.byMonth.isNotEmpty() || rule.byMonthDay.isNotEmpty()) {
                    current.plusDays(1)
                } else {
                    current.plusYears(rule.interval.toLong())
                }
            }
        }
    }

    /**
     * bySetPos 匹配逻辑：
     * - "1MO" = 本月第一个周一
     * - "-1FR" = 本月最后一个周五
     *
     * 涟宝算法（简化版）：
     * 1. 取当月所有匹配 byDay 的日期
     * 2. 取第 N 个（正数）或倒数第 N 个（负数）
     * 3. 命中 date 即视为匹配
     */
    private fun matchesBySetPos(date: LocalDate, rule: RecurrenceRule): Boolean {
        val year = date.year
        val month = date.monthValue
        val lengthOfMonth = date.lengthOfMonth()
        val candidates = mutableListOf<LocalDate>()

        for (day in 1..lengthOfMonth) {
            val candidate = LocalDate.of(year, month, day)
            // 仅依赖 byDay + byMonth（不依赖 byMonthDay，否则会很乱）
            if (rule.byMonth.isNotEmpty() && candidate.monthValue !in rule.byMonth) continue
            if (rule.byDay.isNotEmpty()) {
                val dayMatch = rule.byDay.any { it.dayOfWeek == candidate.dayOfWeek }
                if (!dayMatch) continue
            }
            candidates.add(candidate)
        }

        // 按规则取特定位置
        for (pos in rule.bySetPos) {
            val hit = if (pos > 0) {
                if (pos <= candidates.size) candidates[pos - 1] else null
            } else {
                val fromEnd = -pos
                if (fromEnd <= candidates.size) candidates[candidates.size - fromEnd] else null
            }
            if (hit == date) return true
        }
        return false
    }
}