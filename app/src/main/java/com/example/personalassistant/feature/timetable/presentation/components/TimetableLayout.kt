package com.example.personalassistant.feature.timetable.presentation.components

/**
 * 周网格的纵向布局算式。
 *
 * 抽成纯函数是为了让它能被 JVM 单测直接覆盖 —— Compose 的 Modifier 链要在
 * instrumented test 里才能量到真实尺寸，而这里的算式才是 bug 的真正来源。
 *
 * ## 这个函数存在的理由（Phase 26 修的存量 bug）
 *
 * 原来的写法是：
 * ```kotlin
 * Box(
 *     modifier = Modifier
 *         .height(heightDp)
 *         .padding(top = topDp)   // ← 这里
 * )
 * ```
 *
 * Modifier 从外到内依次作用：`.height(h)` 先把高度定死成 h，紧接着的
 * `.padding(top = t)` 是在这 h **内部**再扣掉 t 像素 —— 子内容拿到的可用高度
 * 变成 `h - t`。排在后面的课（t 大）高度被压到 ≤ 0，整门课在 App 里根本画不出来。
 *
 * 现在改成 `.offset(y = topDp)`：offset 不参与测量，只平移绘制位置，
 * 尺寸仍然是 heightDp。**位置和尺寸必须分开算** —— 这就是下面这个函数存在的意义。
 */
data class CourseSlot(
    /** 距网格顶部的偏移（dp） */
    val topDp: Float,
    /** 课程自身的高度（dp） */
    val heightDp: Float,
)

/**
 * 计算一门课在周网格里的位置与高度。
 *
 * @param startTime 开始时刻（当天分钟数）
 * @param endTime 结束时刻（当天分钟数）
 * @param firstStart 网格第一行的开始时刻（当天分钟数）
 * @param minuteHeightDp 每分钟占多少 dp
 */
fun courseSlot(
    startTime: Int,
    endTime: Int,
    firstStart: Int,
    minuteHeightDp: Float,
): CourseSlot {
    val top = (startTime - firstStart).coerceAtLeast(0) * minuteHeightDp
    val height = (endTime - startTime) * minuteHeightDp
    return CourseSlot(topDp = top, heightDp = height)
}
