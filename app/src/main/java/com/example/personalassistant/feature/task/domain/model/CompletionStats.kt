package com.example.personalassistant.feature.task.domain.model

import java.time.LocalDate

/**
 * Phase 22.6：完成率统计
 *
 * 滴答的「完成率」= **实际完成 ÷ 原计划在该时段的任务数**。
 * 这个分母很关键：只看「完成了几个」会让人虚高 —— 计划了 10 个做 1 个，
 * 完成率是 10% 而不是 100%。
 *
 * @param totalPlanned 该时段原计划的任务数（分母）
 * @param completed 该时段实际完成的任务数（分子）
 * @param overdue 逾期仍未完成的数量（拖延）
 * @param streakDays 连续「每天至少完成 1 个」的天数（从今天往回数）
 */
data class CompletionStats(
    val totalPlanned: Int = 0,
    val completed: Int = 0,
    val overdue: Int = 0,
    val streakDays: Int = 0,
) {
    /** 完成率 [0.0, 1.0]；分母为 0 时算 0（而不是除零炸掉） */
    val completionRate: Float
        get() = if (totalPlanned <= 0) 0f else completed.toFloat() / totalPlanned

    /** 百分比整数（UI 显示用） */
    val completionPercent: Int
        get() = (completionRate * 100).toInt()

    val isEmpty: Boolean
        get() = totalPlanned == 0
}

/**
 * 某一天的计划 / 完成情况（domain 层）
 *
 * @param date 那天（按本地时区）
 * @param planned 那天原计划的任务数
 * @param completed 那天完成的任务数
 */
data class DailyCompletion(
    val date: LocalDate,
    val planned: Int = 0,
    val completed: Int = 0,
)

/**
 * Phase 22.6：完成率统计的纯函数（可 JVM 单测）
 */
object CompletionCalculator {

    /**
     * 汇总一批每日数据 → [CompletionStats]
     *
     * @param daily 每日的计划 / 完成数（可以乱序，内部会按日期排）
     * @param today 今天（连续天数从这天往回数）
     * @param isOverdue 判断某个日期是否算「逾期未完成」（通常是「截止日已过且没完成」）
     */
    fun stats(
        daily: List<DailyCompletion>,
        today: LocalDate = LocalDate.now(),
        isOverdue: (DailyCompletion) -> Boolean = { it.date.isBefore(today) && it.completed < it.planned },
    ): CompletionStats {
        val totalPlanned = daily.sumOf { it.planned }
        val completed = daily.sumOf { it.completed }
        val overdue = daily.filter { isOverdue(it) }
            .sumOf { (it.planned - it.completed).coerceAtLeast(0) }
        return CompletionStats(
            totalPlanned = totalPlanned,
            completed = completed,
            overdue = overdue,
            streakDays = streak(daily, today),
        )
    }

    /**
     * 连续完成天数：从 [today] 往回数，连续「每天至少完成 1 个」的天数。
     *
     * - 今天还没完成任何任务 → 连续天数 0（但昨天起的连续段不该被清零，
     *   所以从**昨天**开始数，见下）
     * - 缺口一天 → 连续段结束
     *
     * ## 为什么从昨天开始数
     *
     * 「今天还没做」是很常见的（尤其早上），如果那样就显示 0 连续天数，
     * 用户会很挫败。连续记录应该看**已经过去的日子**。
     * 但如果今天已经完成了 ≥1 个，那连续段就应该把今天算进去。
     */
    fun streak(
        daily: List<DailyCompletion>,
        today: LocalDate = LocalDate.now(),
    ): Int {
        val byDate = daily.associateBy { it.date }
        // 今天有完成 → 从今天数；今天没完成 → 从昨天数（今天还没过完）
        var cursor = if ((byDate[today]?.completed ?: 0) > 0) today else today.minusDays(1)
        var count = 0
        while (true) {
            val entry = byDate[cursor] ?: break
            if (entry.completed <= 0) break
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }
}
