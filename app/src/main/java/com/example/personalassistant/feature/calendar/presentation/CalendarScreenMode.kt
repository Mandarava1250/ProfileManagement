package com.example.personalassistant.feature.calendar.presentation

/**
 * 日历的三种「看的方式」。
 *
 * 每一种都有独立的 NavHost 路由，收在 [com.example.personalassistant.navigation.Screen.CalendarGraph]
 * 嵌套图里 —— 图内共享同一个 ViewModel，切视图不丢「选中日」。
 */
enum class CalendarScreenMode(val label: String) {
    /** 日：日历日视图（时间轴 + 今日课程 + 预算横条）+ 选中日的日程流 */
    DAY("日"),

    /** 周：周日历 + 工作日日程流（周一~周五）+ 休息日日程流（周六日） */
    WEEK("周"),

    /** 月：月日历网格 + 选中日的标签列表 */
    MONTH("月"),
}

/**
 * 翻页单位 —— 决定左右箭头一次翻多远。
 *
 * 日翻天、周翻周（周视图聚合的是整周）、月翻月。
 */
enum class CalendarNavUnit {
    DAY,
    WEEK,
    MONTH,
}
