package com.example.personalassistant.feature.timetable.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule
import com.example.personalassistant.feature.timetable.domain.model.resolvedLocation
import com.example.personalassistant.feature.timetable.domain.model.resolvedTeacher
// Phase 28：课表上画的是「课时块」（一门课的一个课时）。
// 这里用别名导入，是因为本包里已经有一个同名的 `CourseSlot` —— 那是
// TimetableLayout 里算「块画在哪、多高」用的布局结果类，两者不是一回事。
import com.example.personalassistant.feature.timetable.presentation.CourseSlot as Slot

/**
 * 周视图网格（纵向滚动 + 横向滑动）
 *
 * 视觉：左侧一列整点时间轴 + 右侧 7 列课程方格
 *
 * ## Phase 28：一个课时 = 一个块
 *
 * 以前一门课画一个块；现在**每个课时各画一块**（同一门课的多个块同色）。
 * 块的位置与高度**只跟课时的起止时间有关**（[courseSlot]），
 * 不再读课程身上的「主时段镜像」—— 一门课最后一个课时可能比另一门课晚得多。
 *
 * ## 尺寸基准（Phase 26）
 *
 * - 每分钟 [MINUTE_HEIGHT_DP] dp（与设备高度无关）
 * - 左侧时间轴 [TIMETABLE_AXIS_WIDTH] = 44dp
 * - 每列 [TIMETABLE_COLUMN_WIDTH] = 60dp，**固定宽 + 横向可滑**
 * - 网格可视高 [TIMETABLE_GRID_VIEWPORT_HEIGHT] = 312dp
 *
 * ## 为什么列要固定宽 + 横向滑
 *
 * 之前是「左轴 + 右 7 列 weight=1f 均分剩余宽度」。在 360dp 屏上每列只有
 * (360 - 44) / 7 ≈ 45dp，课程名只能显示两三个字就被截断，而横向其实还有富余：
 * 7 列 × 60dp = 420dp，比可用宽度宽，本来就该能滑。
 *
 * ## 时间轴为什么改成「只画整点」
 *
 * 原来每节课一格，格里塞三行字（节号 + 开始 + 结束）。44dp 的轴宽装不下，
 * 于是节号被挤、时刻被截，而且「节号」这件事在竖向的表里几乎没有信息量。
 * 现在只标整点（08:00 / 09:00 …），跟网格的横向刻度对齐，一眼能定位。
 */
private val MINUTE_HEIGHT_DP = 0.8f

/**
 * 左侧时间轴的固定宽度。
 *
 * ⚠️ **导出为公开常量是刻意的**：TimetableScreen 的 DayHeaderRow 必须与本 grid 的
 * 7 列共用同一套横向基准 —— header 左侧补一个同宽 Spacer 才能对齐。
 * 以前 `val axisWidth = 56.dp` 是函数内的局部变量，header 那边无从引用，
 * 只好各自硬编码，结果两边宽度漂移（实测周一列被顶到最右、标签被切一半）。
 * 现在两边引用同一个值，改宽度只需改这一处。
 */
val TIMETABLE_AXIS_WIDTH = 44.dp

/** 单个日期列的固定宽度 —— 7 列合计 420dp，比屏幕宽，靠横向滑动看全周。 */
val TIMETABLE_COLUMN_WIDTH = 60.dp

/** 网格的可视高度。超出的部分在网格内部纵向滚动。 */
val TIMETABLE_GRID_VIEWPORT_HEIGHT = 312.dp

/**
 * 周视图网格。
 *
 * @param currentDayOfWeek 今天（1..7）；非 null 时该列画「当前时刻」线
 */
@Composable
fun TimetableGrid(
    slotsByDay: Map<Int, List<Slot>>,
    onCourseClick: (Course) -> Unit,
    timeSlotSchedule: TimeSlotSchedule,
    modifier: Modifier = Modifier,
    viewportHeight: Dp = TIMETABLE_GRID_VIEWPORT_HEIGHT,
    currentDayOfWeek: Int? = null,
    /**
     * 横向滚动状态。
     *
     * 由调用方创建并**同时**传给表头行 —— 表头和网格必须共用一个 ScrollState，
     * 否则滑网格时星期标签停在原地，两边对不上。
     */
    hScrollState: ScrollState = rememberScrollState(),
) {
    val days = (1..7).toList()
    // Phase 28：纵向范围 = 课时表范围 ∪ 所有**课时**的范围。
    //
    // 为什么不能只看课程：一门课最后一个课时可能比另一门课晚得多
    // （主时段在周一、最后一个课时在周五晚上）。只按主时段算，
    // 那个晚课时会落到网格内容高度之外 → 直接被裁掉看不见。
    val allSlots = slotsByDay.values.flatten()
    val firstStart = minOf(
        timeSlotSchedule.firstStart,
        allSlots.minOfOrNull { it.session.startTime } ?: timeSlotSchedule.firstStart,
    )
    val lastEnd = maxOf(
        timeSlotSchedule.lastEnd,
        allSlots.maxOfOrNull { it.session.endTime } ?: timeSlotSchedule.lastEnd,
    )
    val totalMinutes = (lastEnd - firstStart).coerceAtLeast(60)
    val contentHeight = (totalMinutes * MINUTE_HEIGHT_DP).dp

    val vScroll = rememberScrollState()
    val nowInk = LocalAppColors.current.nowInk
    // 「现在」的时刻每分钟才算一次；课表打开着不动的话没必要反复重算。
    val nowMinute = remember { DateUtils.currentMinuteOfDay() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(viewportHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vScroll),
        ) {
            Row(modifier = Modifier.height(contentHeight)) {
                // ============ 左侧：时间轴（固定，不随横向滚动）============
                HourAxis(
                    firstStart = firstStart,
                    lastEnd = lastEnd,
                    modifier = Modifier
                        .width(TIMETABLE_AXIS_WIDTH)
                        .height(contentHeight),
                )

                // ============ 右侧：7 列课程区（横向可滑）============
                Row(
                    modifier = Modifier.horizontalScroll(hScrollState),
                ) {
                    days.forEach { day ->
                        DayColumn(
                            day = day,
                            slots = slotsByDay[day].orEmpty(),
                            firstStart = firstStart,
                            lastEnd = lastEnd,
                            onCourseClick = onCourseClick,
                            isToday = day == currentDayOfWeek,
                            nowMinute = nowMinute,
                            nowInk = nowInk,
                            modifier = Modifier
                                .width(TIMETABLE_COLUMN_WIDTH)
                                .height(contentHeight),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左侧时间轴：只标整点。
 *
 * 每一行 = 一小时（60 分钟 × [MINUTE_HEIGHT_DP] = 48dp），标签绝对定位到那一小时
 * 的起始处。标签用 `offset` 而不是 `padding` —— padding 会把内容从容器里扣掉，
 * 绝对定位的标签会被推下去（跟课程卡那个 bug 同源）。
 */
@Composable
private fun HourAxis(
    firstStart: Int,
    lastEnd: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // 从第一个整点开始（firstStart 不一定是整点，比如 08:10 就从 09:00 起标）
        val firstHour = ((firstStart + 59) / 60) * 60
        var minute = firstHour
        while (minute <= lastEnd) {
            val topDp = ((minute - firstStart) * MINUTE_HEIGHT_DP).dp
            Box(
                modifier = Modifier
                    .offset(y = topDp)
                    .padding(start = 2.dp),
            ) {
                Text(
                    text = DateUtils.formatMinutesOfDay(minute),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            minute += 60
        }
    }
}

/**
 * 单日列：Box 绝对定位容器 + 课时按分钟绝对位置
 *
 * - **一个课时 = 一个 Box**，top = (startMinutes - firstStart) × MINUTE_HEIGHT_DP
 * - 跨节 / 跨午休的课，按课时的 startTime/endTime 实际位置渲染
 * - 同一门课的多个块用**同一个课程色**（取 [Slot.course] 的颜色）
 */
@Composable
private fun DayColumn(
    day: Int,
    slots: List<Slot>,
    firstStart: Int,
    lastEnd: Int,
    onCourseClick: (Course) -> Unit,
    isToday: Boolean,
    nowMinute: Int,
    nowInk: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        slots.forEach { slot ->
            val course = slot.course
            val session = slot.session
            // 算式走 courseSlot()，跟 JVM 单测用的是同一份实现。
            // ⚠️ 用的是**课时**的起止时间，不是课程的主时段镜像 ——
            // 一门课同一天的第二个课时可能跟第一门课完全不在一个时段。
            val layout = courseSlot(session.startTime, session.endTime, firstStart, MINUTE_HEIGHT_DP)
            val topDp = layout.topDp.dp
            val heightDp = layout.heightDp.dp
            val courseColor = Color(course.color)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp)
                    // Phase 26 修存量 bug：这里原来是 `.padding(top = topDp)`。
                    //
                    // Modifier 是从外到内依次作用的：`.height(heightDp)` 先把这一项的
                    // 高度定死成 h，紧接着的 `.padding(top = t)` 是在这 h **内部**再扣掉
                    // t 像素留给内边距 —— 子内容的可用高度变成 h - t。
                    // 于是排在后面的课（t 大）内容高度被压到 ≤ 0，整门课渲染不出来；
                    // 排在中间的课则被压扁。9:45 之后课程消失就是这么来的。
                    //
                    // `.offset(y = ...)` 不参与测量，只平移绘制位置，尺寸仍然是 heightDp。
                    .offset(y = topDp)
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(8.dp))
                    // 13% 淡底：课程色是用户自己挑的，可能很浅，
                    // 所以只用一点点色做底，文字走深色保证可读。
                    .background(courseColor.copy(alpha = 0.13f))
                    .clickable { onCourseClick(course) },
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左侧 3dp 实色条 —— 用内层 Box 画，不用 border()：
                    // border 会把四条边都描上，我们只要左边这一条。
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(courseColor),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
                    ) {
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                        )
                        // Phase 28 P10：地点 / 教师用**这个课时**的值（没有就回落课程级）
                        val sessionLocation = session.resolvedLocation(course.location)
                        val sessionTeacher = session.resolvedTeacher(course.teacher)
                        if (sessionLocation.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 2.dp).size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = sessionLocation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        if (sessionTeacher.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 2.dp).size(10.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = sessionTeacher,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ===== 当前时刻线（只在今天那一列）=====
        //
        // 2dp 横线 + 左端 8dp 圆点。用 `now` 固定语义色（#B45309），
        // 不跟主题色走 —— 「现在」是时间事实，不是品牌偏好。
        if (isToday && nowMinute in firstStart..lastEnd) {
            val nowTop = ((nowMinute - firstStart) * MINUTE_HEIGHT_DP).dp
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .offset(y = nowTop)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(nowInk),
                )
                Box(
                    modifier = Modifier
                        .offset(y = nowTop - 3.dp)
                        .size(8.dp)
                        .background(nowInk, CircleShape),
                )
            }
        }
    }
}
