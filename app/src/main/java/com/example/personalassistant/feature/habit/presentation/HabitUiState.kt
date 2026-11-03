package com.example.personalassistant.feature.habit.presentation

import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitStats
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType
import com.example.personalassistant.feature.habit.domain.model.HabitTodayItem

/**
 * Phase 23：习惯页 UI 状态（聚合输出）
 *
 * @param todayItems 今日习惯列表（含今天已打卡次数）
 * @param archivedHabits 已归档的习惯
 * @param stats 当前选中习惯的统计（连续天数 / 月度表 / 热力图）
 * @param selectedHabitId 当前查看统计的习惯 id
 * @param displayYear / displayMonth 月度表与热力图当前显示的年月
 * @param showArchived 是否展开「已归档」区
 * @param editor 新建 / 编辑弹窗的状态（null = 弹窗关闭）
 * @param showInCalendar 23.6 的「日历同屏显示」开关
 * @param message 一次性提示文案（null = 没有）
 */
data class HabitUiState(
    val todayItems: List<HabitTodayItem> = emptyList(),
    val archivedHabits: List<Habit> = emptyList(),
    val stats: HabitStats? = null,
    val selectedHabitId: Long? = null,
    val displayYear: Int = 2024,
    val displayMonth: Int = 1,
    val showArchived: Boolean = false,
    val editor: HabitEditorState? = null,
    val showInCalendar: Boolean = false,
    val message: String? = null,
) {
    /** 当前显示的年月标题（如「2024 年 5 月」） */
    val displayYearMonthText: String get() = "$displayYear 年 $displayMonth 月"
}

/**
 * 新建 / 编辑习惯的弹窗状态
 *
 * [targetCount] 用字符串存 —— 输入框要能显示用户正在敲的「」（空串），
 * 存成 Int 的话空串会变成 0，反而看不出「还没填」。
 */
data class HabitEditorState(
    val id: Long = 0L,
    val name: String = "",
    val unit: String = "",
    val targetType: HabitTargetType = HabitTargetType.DAILY,
    val targetCount: String = "1",
    val iconKey: String = "star",
    val color: Int = 0xFFFF69B4.toInt(),
) {
    /** 是否是编辑（已有 id） */
    val isEditing: Boolean get() = id != 0L
}
