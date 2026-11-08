package com.example.personalassistant.feature.calendar.domain.model

/**
 * 日程流的聚合范围。
 *
 * 「日日程」只看选中的那一天；「工作日 / 休息日」看选中日期所在那一周的
 * 周一~周五 / 周六~周日。
 */
enum class AgendaScope(val label: String) {
    /** 选中的那一天 */
    DAY("日日程"),

    /** 所在那一周的周一 ~ 周五 */
    WORKDAY("工作日日程"),

    /** 所在那一周的周六 ~ 周日 */
    RESTDAY("休息日日程"),
}
