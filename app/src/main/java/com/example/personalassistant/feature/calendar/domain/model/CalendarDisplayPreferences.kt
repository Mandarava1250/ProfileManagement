package com.example.personalassistant.feature.calendar.domain.model

import com.example.personalassistant.feature.task.domain.model.Task

/**
 * Phase 20.3：日历「显示设置」
 *
 * ## 为什么是偏好而不是数据
 *
 * 这四个开关决定的是「日历里显示什么」，不是业务数据 ——
 * 所以它们走 DataStore，**不新增 Room 表**（Room 版本不能动）。
 *
 * ## 四个开关分别管什么
 *
 * - [showCourses]：课程（课表）在日历里显不显示
 * - [showTasks]：清单项（任务）在日历里显不显示
 * - [showCompleted]：已完成的清单项显不显示
 * - [showHabits]：习惯打卡在日历里显不显示
 *
 * ⚠️ [showHabits] 的读写**不在** [com.example.personalassistant.feature.calendar.domain.repository.CalendarDisplayRepository] 里，
 * 而是复用习惯模块自己的 [com.example.personalassistant.feature.habit.domain.repository.HabitDisplayRepository]
 * （key = `habit_show_in_calendar`）—— 两边共用同一个 key，用户只看到一个开关。
 *
 * @property showCourses 是否显示课程，默认 true
 * @property showTasks 是否显示清单项，默认 true
 * @property showCompleted 是否显示已完成的清单项，默认 true
 * @property showHabits 是否在日历里同屏显示习惯打卡，默认 false（与习惯模块一致）
 */
data class CalendarDisplayPreferences(
    val showCourses: Boolean = true,
    val showTasks: Boolean = true,
    val showCompleted: Boolean = true,
    val showHabits: Boolean = false,
) {
    /**
     * 按显示设置过滤清单项
     *
     * - 「清单项」开关关掉 → 一条都不留
     * - 「已完成」开关关掉 → 只留未完成的
     *
     * 放在这里而不是各视图里 —— 日 / 周 / 月三个视图共用同一套口径，
     * 少一处过滤就少一处「月/周/日对不上」的机会。
     */
    fun filterTasks(tasks: List<Task>): List<Task> {
        if (!showTasks) return emptyList()
        if (showCompleted) return tasks
        return tasks.filter { !it.isCompleted }
    }
}
