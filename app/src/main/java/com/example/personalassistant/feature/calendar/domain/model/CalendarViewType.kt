package com.example.personalassistant.feature.calendar.domain.model

/**
 * Phase 7c：日历视图类型
 *
 * 三种视图（与 ViewModel 配合切换）：
 * - DAY：单日时间轴（今日优先，默认落点）
 * - WEEK：一周横向 + 选中日详情（课表 + 任务 + 时间轴）
 * - MONTH：6×7 月视图，按日期显示事件/任务/课程 badge
 *
 * **枚举顺序 = 切换器上的显示顺序**（日 / 周 / 月）。
 * Phase 14 伙伴定调：把「日」放在最前，因为打开 App 最想看的就是今天。
 */
enum class CalendarViewType {
    DAY,
    WEEK,
    MONTH;

    companion object {
        val DEFAULT: CalendarViewType = DAY
    }
}