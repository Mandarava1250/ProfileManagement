package com.example.personalassistant.feature.habit.presentation

import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitStats
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType
import com.example.personalassistant.feature.habit.domain.model.HabitTodayItem
import com.example.personalassistant.feature.habit.domain.util.HabitDates

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

    // ================================================================
    // Phase 26：派生统计
    //
    // 全部是**从已有字段现算**的属性，不新增任何 Flow。
    // 原因：HabitViewModel.uiState 的 combine 已经把 4 条流拼在一起，
    // 再塞一条「统计流」进去只会让订阅链更长，而这几个数字本来就能从
    // todayItems / stats 里直接数出来 —— 多存一份反而多一处可能不同步。
    // ================================================================

    /** 今日已达成目标的习惯数 */
    val todayDoneCount: Int get() = todayItems.count { it.isMet }

    /** 今日习惯总数 */
    val todayTotalCount: Int get() = todayItems.size

    /**
     * 大标题下的副标题（「今日完成 3 / 5 · 连续 12 天」）。
     *
     * 连续天数取的是**当前选中习惯**的（没选就是列表第一个），
     * 所以下面那行统计会把这个习惯的名字也带出来 —— 否则用户分不清
     * 「连续 12 天」说的是哪一个习惯。
     */
    val subtitleText: String
        get() {
            val base = "今日完成 $todayDoneCount / $todayTotalCount"
            val streak = stats?.streakText ?: return base
            return "$base · $streak"
        }

    /**
     * 本周达成率（0f..1f）。
     *
     * ## 为什么要传「今天」
     *
     * 「本周」是个相对概念，离不开「今天是哪天」。而 today 是墙上时钟，
     * 不是状态的一部分 —— 把它做成入参，这个函数就是纯的、可单测的，
     * 「今天」由调用方（UI 层）用 [HabitDates.todayStartMillis] 喂进来。
     *
     * ## 两种目标的算法不一样
     *
     * - **每日 N 次**：本周有几天打了卡 / 本周已过的天数。
     * - **每周 N 次**：本周累计次数 / 目标次数（这一周只要凑够就算达成）。
     *
     * @param today 今天 00:00 的毫秒时间戳
     */
    fun weekCompletionRate(today: Long): Float {
        val stats = stats ?: return 0f
        val todayDate = HabitDates.toLocalDate(today)
        val week = stats.heatmapWeeks.firstOrNull { week ->
            week.cells.any { cell -> HabitDates.toLocalDate(cell.date) == todayDate }
        } ?: return 0f

        // 只算到今天为止 —— 本周还没过的几天不算「没达成」
        val elapsed = week.cells.filter { cell ->
            !HabitDates.toLocalDate(cell.date).isAfter(todayDate)
        }
        if (elapsed.isEmpty()) return 0f

        return if (stats.habit.targetType == HabitTargetType.WEEKLY) {
            val target = stats.habit.targetCount.coerceAtLeast(1)
            (elapsed.sumOf { it.count }.toFloat() / target).coerceIn(0f, 1f)
        } else {
            (elapsed.count { it.count > 0 }.toFloat() / elapsed.size).coerceIn(0f, 1f)
        }
    }

    /** 本周达成率的文案（如「本周达成 62%」）；没有统计时返回 null */
    fun weekCompletionText(today: Long): String? {
        if (stats == null) return null
        val percent = (weekCompletionRate(today) * 100).toInt()
        return "本周达成 $percent%"
    }
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
    // Phase 26：与 Habit.color 同步换成蓝系默认色（见 Habit.kt 的说明）
    val color: Int = 0xFF3F7DE0.toInt(),
) {
    /** 是否是编辑（已有 id） */
    val isEditing: Boolean get() = id != 0L
}
