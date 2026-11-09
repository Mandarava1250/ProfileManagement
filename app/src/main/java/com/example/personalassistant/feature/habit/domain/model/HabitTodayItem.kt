package com.example.personalassistant.feature.habit.domain.model

/**
 * Phase 23：今日习惯列表的一行
 *
 * 把「习惯本身」和「今天已经打了多少次」拼在一起，
 * UI 就不用自己去 combine 两条流再对齐 id。
 */
data class HabitTodayItem(
    val habit: Habit,
    /** 今天已累计次数 */
    val count: Int,
    /** 今天是否已达成目标 */
    val isMet: Boolean,
) {
    /** 进度文案（如「3/8」） */
    val progressText: String get() = "$count/${habit.targetCount}"

    /** 达标后展示的完成文案 */
    val completedText: String get() = "已完成 ${habit.targetCount}${habit.unit.orEmpty()}"
}
