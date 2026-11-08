package com.example.personalassistant.feature.timetable.presentation

/**
 * 课表的两种视图（Phase 26 双视图）。
 *
 * 整周网格适合看**分布**（哪天几节课、哪天空），时间线适合看**具体是什么课**
 * （课程名、地点、教师全部读得全）。两件事本来就得来回看，所以做成标签切换。
 */
enum class TimetableViewType(val label: String) {
    /** 时间线：一天一列到底，课程信息读得全 */
    TIMELINE("时间线"),

    /** 整周网格：7 列横滑，看一周的分布与空档 */
    WEEK_GRID("整周网格"),
    ;

    companion object {
        val ordered: List<TimetableViewType> = entries.toList()
    }
}
