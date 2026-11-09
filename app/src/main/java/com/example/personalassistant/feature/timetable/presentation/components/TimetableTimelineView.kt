package com.example.personalassistant.feature.timetable.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.core.ui.theme.LocalAppSurfaces
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.timetable.domain.model.Course
// Phase 28：时间线上画的也是「课时块」（一门课的一个课时）。
// 别名导入的原因同 TimetableGrid：本包里已有一个同名的 `CourseSlot`（布局结果类）。
import com.example.personalassistant.feature.timetable.presentation.CourseSlot as Slot
import java.time.LocalDate

/**
 * 课表「时间线」视图 —— Phase 26 的双视图之一。
 *
 * ## 为什么要有两个视图
 *
 * 整周网格适合看**分布**（哪天几节课、空不空），但不适合看**具体是什么课**
 * —— 一列 60dp，课程名截得只剩两个字，「高等数学 A」和「体育」长得一样。
 *
 * 时间线反过来：一天一列到底，课程名、地点、教师全部读得全，
 * 代价是看不到整周的空档。两件事本来就得来回看，所以做成两个标签。
 *
 * ## 只显示课程
 *
 * 这里**不显示日程**。课表模块没有 Event 数据源，日程归日历模块
 * （见 [com.example.personalassistant.feature.calendar]）。
 * 原型 D3 里那两行日程是画给「课程 + 日程」混合的场景的，
 * 放进课表会出现一行永远空着的地方。
 *
 * @param selectedDay 当前选中的星期（1..7）
 * @param weekStart 本周周一，用于日期条上的日期数字
 */
@Composable
fun TimetableTimelineView(
    slotsByDay: Map<Int, List<Slot>>,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
    weekStart: LocalDate = currentWeekStart(),
    /**
     * 是否画顶部的 7 天日期条。
     *
     * 整周网格视图下方那段「今日课程」不要日期条 —— 上面已经有一排星期表头了，
     * 再画一遍是重复。
     */
    showDateStrip: Boolean = true,
) {
    val today = remember { LocalDate.now() }
    val todayDayOfWeek = remember { today.dayOfWeek.value }
    val nowInk = LocalAppColors.current.nowInk
    val nowMinute = remember { DateUtils.currentMinuteOfDay() }

    // Phase 28：一天的几行 = 一天的几个课时（同一门课两个课时就是两行）
    val slots = slotsByDay[selectedDay].orEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        // ===== 日期条：7 天，今天高亮 =====
        if (showDateStrip) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            (1..7).forEach { day ->
                val date = weekStart.plusDays((day - 1).toLong())
                val isToday = date == today
                val isSelected = day == selectedDay
                Column(
                    modifier = Modifier
                        .width(TIMETABLE_COLUMN_WIDTH)
                        .clickable { onSelectDay(day) }
                        .padding(vertical = AppSpacing.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = dayLabels[day - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 今天：22dp 品牌蓝圆 + 白字
                    Box(
                        modifier = Modifier
                            .padding(top = AppSpacing.xxs)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        }

        val surfaces = LocalAppSurfaces.current
        HorizontalDivider(
            thickness = 1.dp,
            color = surfaces.divider,
        )

        if (slots.isEmpty()) {
            Text(
                text = "这一天没有课",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(AppSpacing.lg),
            )
            return@Column
        }

        // ===== 课时时间线 =====
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 「现在」标记插在「下一节课」之前 —— 今天的课才插。
            // ⚠️ 按**课时**的开始时间比：一门课同一天的第二个课时才是真正的「下一节」。
            val nowIndex = if (selectedDay == todayDayOfWeek) {
                slots.indexOfFirst { it.session.startTime > nowMinute }
                    .let { if (it < 0) slots.size else it }
            } else {
                -1
            }

            itemsIndexed(slots) { index, slot ->
                if (index == nowIndex) {
                    NowMarker(nowInk = nowInk, nowMinute = nowMinute)
                }
                TimelineCourseRow(
                    slot = slot,
                    onClick = { onCourseClick(slot.course) },
                )
            }
            if (nowIndex == slots.size) {
                item { NowMarker(nowInk = nowInk, nowMinute = nowMinute) }
            }
        }
    }
}

/**
 * 一行课时：44dp 时刻列 + 3dp 竖条 + 课程名 + 地点 / 教师
 *
 * 时刻取的是**课时**的起止时间（不是课程的主时段镜像）——
 * 同一门课同一天的两个课时在时间线上是两行，时刻各不相同。
 */
@Composable
private fun TimelineCourseRow(
    slot: Slot,
    onClick: () -> Unit,
) {
    val course = slot.course
    val session = slot.session
    val courseColor = Color(course.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        // 44dp 时刻列
        Column(
            modifier = Modifier.width(44.dp),
        ) {
            Text(
                text = DateUtils.formatMinutesOfDay(session.startTime),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = DateUtils.formatMinutesOfDay(session.endTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(AppSpacing.sm))

        // 3dp 分类色竖条 + 内容
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(courseColor),
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (course.location.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(AppSpacing.xxs))
                        Text(
                            text = course.location,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (course.teacher.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(AppSpacing.xxs))
                        Text(
                            text = course.teacher,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 「现在」标记行 —— 2dp 横线 + 8dp 圆点 + 时刻文字 */
@Composable
private fun NowMarker(
    nowInk: Color,
    nowMinute: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DateUtils.formatMinutesOfDay(nowMinute),
            style = MaterialTheme.typography.labelSmall,
            color = nowInk,
            modifier = Modifier.width(44.dp),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(nowInk),
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(nowInk),
        )
    }
}

/** 周一到周日 */
private val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 本周周一 */
private fun currentWeekStart(): LocalDate {
    val today = LocalDate.now()
    return today.minusDays((today.dayOfWeek.value - 1).toLong())
}
