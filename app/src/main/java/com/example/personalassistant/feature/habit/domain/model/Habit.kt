package com.example.personalassistant.feature.habit.domain.model

/**
 * Phase 23：习惯（Domain 模型）
 *
 * 一个习惯 = 「想养成的事」+「达成目标的方式」+「显示信息」。
 *
 * - [targetType] + [targetCount]：目标怎么算（每天 N 次 / 每周 N 次）
 * - [unit]：单位（「杯」「页」「分钟」），只用于展示，null = 不显示单位
 * - [sortOrder]：列表里的手动顺序（值越小越靠前）
 * - [isArchived]：归档后不再出现在「今日」列表里，但历史打卡记录保留
 */
data class Habit(
    val id: Long = 0L,
    val name: String,
    val iconKey: String = "star",
    val color: Int = 0xFFFF69B4.toInt(),
    val targetType: HabitTargetType = HabitTargetType.DAILY,
    val targetCount: Int = 1,
    val unit: String? = null,
    val sortOrder: Long = 0L,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** 目标描述（如「每天 8 次」「每周 3 次」） */
    val targetDescription: String
        get() = when (targetType) {
            HabitTargetType.DAILY -> if (unit.isNullOrBlank()) "每天 $targetCount 次" else "每天 $targetCount $unit"
            HabitTargetType.WEEKLY -> if (unit.isNullOrBlank()) "每周 $targetCount 次" else "每周 $targetCount $unit"
        }
}
