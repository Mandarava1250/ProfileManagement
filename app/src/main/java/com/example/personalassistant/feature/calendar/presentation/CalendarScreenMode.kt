package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.feature.calendar.domain.model.AgendaScope

/**
 * 日历的六种「看的方式」。
 *
 * 每一种都有独立的 NavHost 路由，这个枚举就是「路由 → 页面行为」的映射表。
 *
 * @param agendaScope 非空 = 这一屏是日程流，聚合范围由它决定；null = 网格视图
 */
enum class CalendarScreenMode(
    val label: String,
    val agendaScope: AgendaScope?,
) {
    /** 日日程：只看选中的那一天 */
    AGENDA_DAY("日日程", AgendaScope.DAY),

    /** 工作日日程：所在那一周的周一 ~ 周五 */
    AGENDA_WORKDAY("工作日日程", AgendaScope.WORKDAY),

    /** 休息日日程：所在那一周的周六 ~ 周日 */
    AGENDA_RESTDAY("休息日日程", AgendaScope.RESTDAY),

    /** 月视图 */
    MONTH("月", null),

    /** 周视图 */
    WEEK("周", null),

    /** 日视图（时间轴） */
    DAY("日", null),
}

/**
 * 翻页单位 —— 决定左右箭头一次翻多远。
 *
 * 「日日程」翻天、「工作日 / 休息日日程」翻周（聚合的是整周）、「月」翻月。
 */
enum class CalendarNavUnit {
    DAY,
    WEEK,
    MONTH,
}
