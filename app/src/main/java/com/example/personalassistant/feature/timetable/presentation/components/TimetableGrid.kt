package com.example.personalassistant.feature.timetable.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.Period
import com.example.personalassistant.feature.timetable.domain.model.TimeSlotSchedule

/**
 * 周视图网格（可纵向滚动版）
 *
 * 视觉：左侧一列"第 N 节 / 开始-结束"时间轴 + 右侧 7 列课程方格
 *
 * 实现要点：
 *  - 固定每分钟占 MINUTE_HEIGHT_DP dp（与设备高度无关）
 *  - 整张 grid 内容高度 = (lastEnd - firstStart) × MINUTE_HEIGHT_DP
 *  - 外层套 `verticalScroll`：**内容超出可视区时下滑查看，不压缩行高**
 *  - 行高 = duration × MINUTE_HEIGHT_DP；课程 top/height 也按这个常量算
 *  - 跨节 / 跨午休的课按实际起止渲染，不依赖 Period 边界
 */
private val MINUTE_HEIGHT_DP = 0.9f

/**
 * 左侧时间轴的固定宽度。
 *
 * ⚠️ **导出为公开常量是刻意的**：TimetableScreen 的 DayHeaderRow 必须与本 grid 的
 * 7 列共用同一套横向基准 —— header 左侧补一个同宽 Spacer 才能对齐。
 * 以前 `val axisWidth = 56.dp` 是函数内的局部变量，header 那边无从引用，
 * 只好各自硬编码，结果两边宽度漂移（实测周一列被顶到最右、标签被切一半）。
 * 现在两边引用同一个值，改宽度只需改这一处。
 */
val TIMETABLE_AXIS_WIDTH = 56.dp

@Composable
fun TimetableGrid(
    coursesByDay: Map<Int, List<Course>>,
    onCourseClick: (Course) -> Unit,
    timeSlotSchedule: TimeSlotSchedule,
    modifier: Modifier = Modifier,
) {
    val days = (1..7).toList()
    val periods = timeSlotSchedule.periods
    val firstStart = timeSlotSchedule.firstStart
    val lastEnd = timeSlotSchedule.lastEnd
    val totalMinutes = (lastEnd - firstStart).coerceAtLeast(60)
    val contentHeight = (totalMinutes * MINUTE_HEIGHT_DP).dp

    Box(modifier = modifier.fillMaxSize()) {
        // 整张 grid 放在可滚动容器里
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight),
            ) {
                // ============ 左侧：时间轴 ============
                Column(
                    modifier = Modifier
                        .width(TIMETABLE_AXIS_WIDTH)
                        .height(contentHeight)
                        .padding(end = 2.dp),
                ) {
                    periods.forEach { period ->
                        PeriodAxisCell(
                            period = period,
                            firstStart = firstStart,
                        )
                    }
                }

                // ============ 右侧：7 列课程区 ============
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(contentHeight),
                ) {
                    days.forEach { day ->
                        DayColumn(
                            day = day,
                            courses = coursesByDay[day].orEmpty(),
                            firstStart = firstStart,
                            onCourseClick = onCourseClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(contentHeight)
                                .padding(horizontal = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左侧时间轴的单行 = 1 个 Period
 *
 *  3 行布局，56dp 轴宽里绝对不会被截：
 *  - 第 1 行：粗体节号 "1"
 *  - 第 2 行：开始时间 "08:00"
 *  - 第 3 行：结束时间 "08:45"
 *
 * 之前用单行 "08:00-08:45"（11 字符）在 56dp 宽里被 maxLines=1 截成 "08:00-08:"。
 * 分行后每行最多 5 字符 "HH:mm"，稳放得下；节次与时段一一对应也更直观。
 */
@Composable
private fun PeriodAxisCell(
    period: Period,
    firstStart: Int,
) {
    val heightDp = (period.durationMinutes * MINUTE_HEIGHT_DP).dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${period.index}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = DateUtils.formatMinutesOfDay(period.startMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = DateUtils.formatMinutesOfDay(period.endMinutes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 单日列：Box 绝对定位容器 + 课程按分钟绝对位置
 *
 *  - 每门课 = 一个 Box，top = (startMinutes - firstStart) × MINUTE_HEIGHT_DP
 *  - 跨节 / 跨午休的课，按 startTime/endTime 实际位置渲染
 */
@Composable
private fun DayColumn(
    day: Int,
    courses: List<Course>,
    firstStart: Int,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        courses.forEach { course ->
            val topDp = ((course.startTime - firstStart).coerceAtLeast(0) * MINUTE_HEIGHT_DP).dp
            val heightDp = ((course.endTime - course.startTime) * MINUTE_HEIGHT_DP).dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp)
                    .padding(top = topDp)
                    .padding(horizontal = 1.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        color = androidx.compose.ui.graphics.Color(course.color).copy(alpha = 0.18f)
                    )
                    .border(
                        width = 0.5.dp,
                        color = androidx.compose.ui.graphics.Color(course.color).copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(2.dp)
                    .clickable { onCourseClick(course) },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                    if (course.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 2.dp).size(10.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = course.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    if (course.teacher.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 2.dp).size(10.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = course.teacher,
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
}
