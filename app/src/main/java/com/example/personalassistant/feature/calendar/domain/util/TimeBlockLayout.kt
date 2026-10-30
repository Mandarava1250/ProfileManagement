package com.example.personalassistant.feature.calendar.domain.util

/**
 * 时间轴布局的纯计算 —— Phase 14 樱花粉 2.0
 *
 * 日历的「日」视图（今日）与「周」视图共用同一套 **1 小时行轴**：
 * 每一行代表一个小时，课程与事件按开始时间落到某一行，按时长占若干行。
 *
 * 这里只做纯计算，不依赖任何 Compose 类型 —— 所以能写 JVM 单测，
 * 边界情况（零点、跨日、零时长、重叠）在跑 App 之前就能验完。
 */
object TimeBlockLayout {

    /** 一行 = 1 小时 */
    const val MINUTES_PER_ROW = 60

    /** 一天的分钟数上限（24:00） */
    const val MINUTES_PER_DAY = 24 * 60

    /**
     * 一个时间块在时间轴上的行位置
     *
     * @param rowOffset 从第几行开始（0 = 00:00 那一行）
     * @param rowSpan 占几行（至少 1 行）
     */
    data class RowSpan(val rowOffset: Int, val rowSpan: Int)

    /**
     * 时间块在「同一列里」的分栏结果
     *
     * @param columnIndex 落在第几列（从 0 开始）
     * @param columnCount 这一组总共分成了几列
     */
    data class ColumnPlacement(val columnIndex: Int, val columnCount: Int)

    /**
     * 时间区间（当天分钟数）
     *
     * @param startMinute 开始时间，0..1439
     * @param endMinute 结束时间，0..1440
     */
    data class TimeRange(val startMinute: Int, val endMinute: Int)

    /**
     * 把「当天分钟数」换算成时间轴上的行位置
     *
     * 规则：
     * - 起点向下取整到整小时（08:30 落在第 8 行，也就是 08:00 那一行）
     * - 时长向上取整到整小时（08:00–09:40 占 2 行）
     * - 零时长也占 1 行 —— 高度为 0 的块在屏幕上等于看不见
     * - 越界数据夹到 `[0, 1440]`，不会画出屏幕外
     */
    fun toRowSpan(startMinute: Int, endMinute: Int): RowSpan {
        val start = startMinute.coerceIn(0, MINUTES_PER_DAY)
        // end 小于 start 是脏数据（课程结束早于开始），按零时长处理而不是画到屏幕外
        val end = endMinute.coerceIn(start, MINUTES_PER_DAY)
        val offset = start / MINUTES_PER_ROW
        val span = ((end - start + MINUTES_PER_ROW - 1) / MINUTES_PER_ROW).coerceAtLeast(1)
        return RowSpan(offset, span)
    }

    /**
     * 给一组时间区间分配列 —— 同一列里重叠的块左右分栏
     *
     * 用的是经典的区间图着色贪心：按起点升序，每个区间放进第一个
     * 「该列上一个区间的终点 ≤ 本区间起点」的列；全都放不下就新开一列。
     * 贪心能保证用到的列数是最少的 —— 6 门课挤在 8:00–10:00 就分 2 列，
     * 而不是每门课各占一列。
     *
     * @return 与入参一一对应的分栏结果
     */
    fun assignColumns(ranges: List<TimeRange>): List<ColumnPlacement> {
        if (ranges.isEmpty()) return emptyList()

        // 按开始时间升序；开始时间相同时按结束时间升序（短的先放）
        val order = ranges.indices.sortedWith(
            compareBy(
                { ranges[it].startMinute.coerceIn(0, MINUTES_PER_DAY) },
                { ranges[it].endMinute.coerceIn(0, MINUTES_PER_DAY) },
            )
        )

        // 每列当前最后一个区间的终点
        val columnEnds = mutableListOf<Int>()
        // 每个区间落在第几列
        val columnOf = IntArray(ranges.size)

        for (index in order) {
            val start = ranges[index].startMinute.coerceIn(0, MINUTES_PER_DAY)
            val end = ranges[index].endMinute.coerceIn(start, MINUTES_PER_DAY)

            val free = columnEnds.indexOfFirst { it <= start }
            if (free >= 0) {
                columnEnds[free] = end
                columnOf[index] = free
            } else {
                columnEnds.add(end)
                columnOf[index] = columnEnds.lastIndex
            }
        }

        val columnCount = columnEnds.size
        return columnOf.map { ColumnPlacement(it, columnCount) }
    }

    /**
     * 一步到位：算出某个区间在时间轴上的行位置与分栏
     */
    fun layout(startMinute: Int, endMinute: Int, columnIndex: Int, columnCount: Int): Pair<RowSpan, ColumnPlacement> =
        toRowSpan(startMinute, endMinute) to ColumnPlacement(columnIndex, columnCount)
}
