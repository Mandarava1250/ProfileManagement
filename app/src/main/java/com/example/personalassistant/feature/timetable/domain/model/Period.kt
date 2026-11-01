package com.example.personalassistant.feature.timetable.domain.model

/**
 * 一个课时（"第 N 节"）的定义
 *
 * 设计要点：
 *  - index 从 1 开始（"第 1 节"），符合学生直觉
 *  - startMinutes / endMinutes 是当天分钟数（0..1439），与 [Course.startTime] 同坐标系，
 *    渲染 grid 时直接用此坐标做绝对定位
 *  - [Duration] 是便捷属性，不参与 init 校验（避免在 setter 时反复 require）
 *  - init 校验：start < end、end <= 1440（end 允许等于 1440 即"到 24:00 结束"，
 *    但更常见的"第 12 节结束在 21:20"是合法值）
 *
 * 横向关系：
 *  - 课时表（[TimeSlotSchedule]）是 N 个 [Period] 的列表
 *  - [Course.startTime] / [Course.endTime] 必须落在 [Period] 的范围内
 *    —— Course 渲染时按"起始 Period + 跨越 Period 数"决定 grid 行号
 */
data class Period(
    val index: Int,
    val startMinutes: Int,
    val endMinutes: Int,
) {
    init {
        require(index >= 1) { "Period.index 必须 >= 1，实际=$index" }
        require(startMinutes in 0..1439) { "startMinutes 必须在 0..1439，实际=$startMinutes" }
        require(endMinutes in 0..1440) { "endMinutes 必须在 0..1440，实际=$endMinutes" }
        require(endMinutes > startMinutes) { "endMinutes 必须 > startMinutes（$startMinutes→$endMinutes）" }
    }

    /** 这节课时长（分钟） */
    val durationMinutes: Int get() = endMinutes - startMinutes

    /**
     * 该 Period 是否包含给定分钟。
     *
     * - 半开区间 [start, end)：与 Course 的"endTime > startTime 冲突判断"语义保持一致
     */
    fun contains(minutes: Int): Boolean = minutes in startMinutes until endMinutes

    companion object {
        /**
         * 默认 12 节课的"上午 8:00 起 + 45min 课 + 10min 休息"课时表
         *
         * 时间线（与用户截图一致）：
         *  1  08:00-08:45
         *  2  08:55-09:40
         *  3  09:55-10:40
         *  4  10:50-11:35
         *  ─── 午休 ───
         *  5  13:30-14:15
         *  6  14:25-15:10
         *  7  15:25-16:10
         *  8  16:20-17:05
         *  9  18:00-18:45
         * 10  18:55-19:40
         * 11  19:50-20:35
         * 12  20:35-21:20
         *
         * 注意：12 节课总时长 12*45=540 min；包含午休 13:35-13:30=1h55min + 课间 8*10=80 min，
         * 整体覆盖 08:00-21:20=13h20min。
         */
        val DEFAULT_12: List<Period> = listOf(
            Period(1,  8 * 60,         8 * 60 + 45),    // 08:00-08:45
            Period(2,  8 * 60 + 55,    9 * 60 + 40),    // 08:55-09:40
            Period(3,  9 * 60 + 55,   10 * 60 + 40),    // 09:55-10:40
            Period(4, 10 * 60 + 50,   11 * 60 + 35),    // 10:50-11:35
            Period(5, 13 * 60 + 30,   14 * 60 + 15),    // 13:30-14:15
            Period(6, 14 * 60 + 25,   15 * 60 + 10),    // 14:25-15:10
            Period(7, 15 * 60 + 25,   16 * 60 + 10),    // 15:25-16:10
            Period(8, 16 * 60 + 20,   17 * 60 +  5),    // 16:20-17:05
            Period(9, 18 * 60,        18 * 60 + 45),   // 18:00-18:45
            Period(10, 18 * 60 + 55,  19 * 60 + 40),   // 18:55-19:40
            Period(11, 19 * 60 + 50,  20 * 60 + 35),   // 19:50-20:35
            Period(12, 20 * 60 + 35,  21 * 60 + 20),   // 20:35-21:20
        )
    }
}

/**
 * 整张"课时表"：N 个 [Period] 的有序列表
 *
 *  - 用 [List] 而非 Set：用户可能配出"两节相同时间"（虽然不推荐），保持语义简单
 *  - 提供工具方法 [periodOf] / [firstPeriodAtOrAfter] 给 Course 渲染时定位使用
 *  - 校验：index 严格递增 1..N；时间段按 [Period.contains] 不重叠（防用户错配）
 */
data class TimeSlotSchedule(
    val periods: List<Period>,
) {
    init {
        // 课时数量合理上下界：2 节课太少，20 节课太密
        require(periods.size in 1..20) { "课时数必须在 1..20，实际=${periods.size}" }
        // index 必须 1..N
        periods.forEachIndexed { i, p ->
            require(p.index == i + 1) {
                "Period.index 必须从 1 严格递增，期望 ${i + 1} 实际 ${p.index}"
            }
        }
        // 两两时间段不重叠
        for (i in 1 until periods.size) {
            val prev = periods[i - 1]
            val curr = periods[i]
            require(curr.startMinutes >= prev.endMinutes) {
                "课时 ${prev.index}(${prev.startMinutes}-${prev.endMinutes}) 与 " +
                    "课时 ${curr.index}(${curr.startMinutes}-${curr.endMinutes}) 重叠"
            }
        }
    }

    /**
     * 给定 [minutes]，返回**包含该分钟**的第一个 Period；
     * 都不包含时返回 null（落在课间或表外）
     */
    fun periodOf(minutes: Int): Period? = periods.firstOrNull { it.contains(minutes) }

    /**
     * 给定 [minutes]，返回**起始时间不晚于 minutes**的最后一个 Period；
     * minutes 早于第一节起始时返回 null
     */
    fun lastPeriodStartingAtOrBefore(minutes: Int): Period? =
        periods.lastOrNull { it.startMinutes <= minutes }

    val firstStart: Int get() = periods.first().startMinutes
    val lastEnd: Int get() = periods.last().endMinutes

    val size: Int get() = periods.size

    companion object {
        /** 用户从未配过时给一个空集合之外的安全默认：使用 [Period.DEFAULT_12] */
        fun default(): TimeSlotSchedule = TimeSlotSchedule(Period.DEFAULT_12)
    }
}
