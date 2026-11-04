package com.example.personalassistant.feature.calendar.domain.model

/**
 * Phase 24B：日历侧的习惯打卡条目
 *
 * ## 为什么是日历自己的模型
 *
 * 习惯模块有自己的 Habit 模型，但日历域不想直接把别人的模型搬进来 ——
 * 习惯那边加字段、日历这边就得跟着改。
 * 所以这里只留日历真正要用到的几个字段：够画一行、够算一个角标就够了。
 *
 * @property habitId 习惯 id（打卡 / 撤销时回传给 habit 域）
 * @property name 习惯名
 * @property color 习惯色（ARGB）
 * @property count 当天的累计次数
 * @property targetCount 目标次数
 * @property isMet 当天是否达成目标
 */
data class CalendarHabitItem(
    val habitId: Long,
    val name: String,
    val color: Int,
    val count: Int,
    val targetCount: Int,
    val isMet: Boolean,
) {
    /** 进度文案（如「3/8」） */
    val progressText: String get() = "$count/$targetCount"
}
