package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.core.ui.theme.onColorFor
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.util.CalendarTaskFilter
import com.example.personalassistant.feature.calendar.domain.util.CalendarTimeRange
import com.example.personalassistant.feature.calendar.domain.util.TimeBlockLayout
import com.example.personalassistant.feature.calendar.domain.util.sessionsOn
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.model.resolvedLocation
import java.time.Instant
import java.time.ZoneId

/**
 * 1 小时行轴上的时间块
 *
 * @param key 唯一键
 * @param title 主标题
 * @param subtitle 副标题（地点 / 分类 / 优先级）
 * @param startMinute 从 00:00 起的分钟数
 * @param endMinute 结束分钟数
 * @param color 块的颜色
 * @param filled true = 实底（课程），false = 淡底（事件 / 任务）
 * @param isCompleted true = 已完成（划线 + 淡化）。Phase 15 P0-1.3 补的字段 ——
 *   之前这个块根本没有完成态，于是时间轴上的任务永远不会变灰
 * @param crossesMidnight Phase 20.7：这块属于一个**跨午夜**的时段
 *   （当天这半截画到 24:00，次日那半截从 00:00 开始），UI 上标「跨天」
 */
data class TimelineBlock(
    val key: String,
    val title: String,
    val subtitle: String?,
    val startMinute: Int,
    val endMinute: Int,
    val color: Color,
    val filled: Boolean = false,
    val isCompleted: Boolean = false,
    val crossesMidnight: Boolean = false,
)

/**
 * 1 小时行轴（Phase 14）
 *
 * 课程、事件、任务都会变成轴上的块，块的高度就是它真实占的时长。
 * 定位算法在 [TimeBlockLayout] 里，是纯函数，可单测。
 *
 * 日视图和周视图共用这一份 —— 两边的差别只是「取哪一天的数据」，
 * 画法必须一样，否则用户在两个视图之间来回切会看到不同的世界。
 *
 * @param courses 当天的课程
 * @param timedEvents 当天带时间的事件
 * @param tasks 当天的任务（带截止时间才会落到轴上）
 * @param emptyHint 空轴时的提示文案
 * @param dayStart 当天 00:00 的时间戳 —— 给了它才会做跨午夜的切段（Phase 20.7）
 *   与「带时间段的清单项按真实时段占位」（Phase 20.4）
 */
@Composable
fun DayTimelineView(
    courses: List<Course>,
    timedEvents: List<Event>,
    tasks: List<Task> = emptyList(),
    emptyHint: String = "今天没有课程和日程",
    showNowLine: Boolean = false,
    dayStart: Long? = null,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val blocks = remember(courses, timedEvents, tasks, primaryColor, dayStart) {
        buildTimelineBlocks(courses, timedEvents, tasks, primaryColor, dayStart)
    }

    if (blocks.isEmpty()) {
        EmptyTimelineHint(text = emptyHint)
        return
    }

    val hourHeight = 56.dp
    val gutterWidth = 42.dp
    val columnGap = 3.dp
    // 进入时取一次当前时刻就固定住 —— 红线跟着秒针跳的话看着晕
    val nowMinute = remember { DateUtils.currentMinuteOfDay() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val totalWidth = maxWidth
        val ranges = blocks.map { TimeBlockLayout.TimeRange(it.startMinute, it.endMinute) }
        val placements = TimeBlockLayout.assignColumns(ranges)
        val columnCount = placements.maxOfOrNull { it.columnCount } ?: 1
        val columnWidth = (totalWidth - gutterWidth - columnGap * (columnCount - 1)) / columnCount

        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧：小时刻度
            Column(modifier = Modifier.width(gutterWidth)) {
                repeat(24) { hour ->
                    Box(modifier = Modifier.height(hourHeight), contentAlignment = Alignment.TopStart) {
                        Text(
                            text = String.format(java.util.Locale.CHINA, "%02d:00", hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        )
                    }
                }
            }

            // 右侧：网格线 + 时间块
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(hourHeight * 24),
            ) {
                repeat(24) { hour ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            .align(Alignment.TopStart)
                            .offset(y = hourHeight * hour),
                    )
                }

                // Phase 14：「现在」线。
                // 24 小时的轴一眼望过去看不出「现在几点」，画一条线就一目了然 ——
                // 线上方是已经过去的，下面是还没到的。
                //
                // 用固定的 `now` 语义色（#B45309），不跟主题色走 ——
                // 「现在」是时间事实，不是品牌偏好。之前用 colorScheme.error（红），
                // 跟「冲突 / 逾期」撞色，看到一条红线会以为出错了。
                val nowInk = LocalAppColors.current.nowInk
                if (showNowLine) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(nowInk)
                            .align(Alignment.TopStart)
                            .offset(y = hourHeight * (nowMinute / 60f)),
                    )
                }

                blocks.forEachIndexed { index, block ->
                    val span = TimeBlockLayout.toRowSpan(block.startMinute, block.endMinute)
                    val placement = placements[index]
                    // Phase 20.7：跨午夜的块标一下「跨天」——
                    // 它在另一头还有一截（当天的画到 24:00，次日的从 00:00 开始）
                    val timeText = buildString {
                        append(DateUtils.formatMinutesOfDay(block.startMinute))
                        append(" - ")
                        append(DateUtils.formatMinutesOfDay(block.endMinute))
                        if (block.crossesMidnight) append(" · 跨天")
                    }
                    TimelineBlockCard(
                        block = block,
                        timeText = timeText,
                        modifier = Modifier
                            .offset(
                                x = (columnWidth + columnGap) * placement.columnIndex,
                                y = hourHeight * span.rowOffset,
                            )
                            .width(columnWidth)
                            .height(hourHeight * span.rowSpan - 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * 把三类数据摊平成轴上的块
 *
 * 单独抽出来是为了能脱离 Compose 直接测 —— 「任务该落在几点」这种事，
 * 光看界面是验证不了的。
 *
 * @param dayStart 当天 00:00 的时间戳。给了它：
 * - 事件按「跨天条」切段（Phase 20.7，跨午夜不截断）
 * - 带时间段的清单项按真实时段占位（Phase 20.4，与课程同一条轴）
 * 不给（null）= 走 Phase 14/15 的老路径（按「当天分钟数」换算）
 */
internal fun buildTimelineBlocks(
    courses: List<Course>,
    timedEvents: List<Event>,
    tasks: List<Task>,
    primaryColor: Color,
    dayStart: Long? = null,
): List<TimelineBlock> {
    val courseBlocks = courses.flatMap { course ->
        // Phase 28 P6：一个课时 = 一个块 —— 一门课同一天两个课时，轴上占两段
        val sessions = course.sessionsOn(course.dayOfWeek)
        sessions.map { session ->
            TimelineBlock(
                // 只有一段课时时保持原来的 key（course-<id>）；
                // 多段时带上课时的序号 —— 两个块不能撞 key
                key = if (sessions.size > 1) {
                    "course-${course.id}-${session.sortOrder}"
                } else {
                    "course-${course.id}"
                },
                title = course.name,
                // Phase 28 P10：地点用**这个课时**的值（没有就回落课程级）
                subtitle = session.resolvedLocation(course.location).takeIf { it.isNotBlank() },
                startMinute = session.startTime,
                endMinute = session.endTime,
                color = Color(course.color),
                filled = true,
            )
        }
    }
    val eventBlocks = timedEvents.mapNotNull { event ->
        // Phase 15：Event.startTime / endTime 是 **epoch 毫秒时间戳**
        // （Event.kt:13、EventEntity.kt:48-54、EventTimeMapper.toSlice 三处一致）。
        // 之前这里写的是 `event.startTime?.toInt()` —— 把毫秒戳直接截断成 Int 当分钟数用，
        // 2026 年的毫秒戳截断后是个大乱数，事件在时间轴上的位置全是错的。
        // 现在统一走 minuteOfDay，把毫秒转成当天分钟数。
        val startMillis = event.startTime ?: return@mapNotNull null

        if (dayStart != null) {
            // Phase 20.7：按「跨天条」切段 —— 跨午夜不截断
            val endMillis = event.endTime ?: startMillis
            val segment = CalendarTimeRange
                .segmentOn(startMillis, endMillis, dayStart) ?: return@mapNotNull null
            return@mapNotNull TimelineBlock(
                key = "event-${event.id}",
                title = event.title,
                subtitle = event.category,
                startMinute = segment.startMinute,
                endMinute = segment.endMinute,
                color = Color(event.color),
                crossesMidnight = segment.crossesMidnight,
            )
        }

        val start = minuteOfDay(startMillis) ?: return@mapNotNull null
        // 结束时间：取不到（没填 / 正好落在 00:00）或落在次日导致比开始还早 —— 都按 1 小时算
        // （零高度的块在屏幕上等于不存在）
        val rawEnd = event.endTime?.let { minuteOfDay(it) }
        val end = if (rawEnd == null || rawEnd <= start) start + TimeBlockLayout.MINUTES_PER_ROW else rawEnd
        TimelineBlock(
            key = "event-${event.id}",
            title = event.title,
            subtitle = event.category,
            startMinute = start,
            endMinute = end,
            color = Color(event.color),
        )
    }
    val taskBlocks = tasks.mapNotNull { task ->
        // Phase 20.4 + 20.2：带时间段的清单项按**真实时段**占位（与课程同一条轴）
        if (dayStart != null &&
            CalendarTaskFilter.isTimed(task)
        ) {
            val segment = CalendarTaskFilter
                .segmentOn(task, dayStart) ?: return@mapNotNull null
            return@mapNotNull TimelineBlock(
                key = "task-${task.id}",
                title = task.title,
                subtitle = task.priority.label,
                startMinute = segment.startMinute,
                endMinute = segment.endMinute,
                color = primaryColor,
                isCompleted = task.isCompleted,
                crossesMidnight = segment.crossesMidnight,
            )
        }
        // 没填时间段的清单项：按截止时刻占 1 小时（Phase 14 的老行为）
        val due = task.dueDate ?: return@mapNotNull null
        val minute = minuteOfDay(due) ?: return@mapNotNull null
        TimelineBlock(
            key = "task-${task.id}",
            title = task.title,
            subtitle = task.priority.label,
            startMinute = minute,
            // 任务没有时长概念，统一按 1 小时占位
            endMinute = minute + TimeBlockLayout.MINUTES_PER_ROW,
            color = primaryColor,
            // Phase 15 P0-1.3：完成态透传到卡片 —— 之前这里没带，
            // 时间轴上的任务块就永远不变灰
            isCompleted = task.isCompleted,
        )
    }
    return (courseBlocks + eventBlocks + taskBlocks).sortedBy { it.startMinute }
}

/**
 * 毫秒时间戳 → 当天第几分钟（0..1439）
 *
 * 返回 null 只有一种情况：时间戳正好落在**本地时区的 00:00**。
 * 00:00 多半是「只有日期没有时刻」的默认值 —— 落在轴上没有意义。
 *
 * ⚠️ 这里**不判断是不是当天**：日期过滤在上游（按天查事件 / 任务）做完了，
 * 这个函数只负责把时刻换算成 0..1439 的分钟数。
 */
internal fun minuteOfDay(millis: Long): Int? {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
    if (time.hour == 0 && time.minute == 0) {
        // 00:00 多半是「只有日期没有时刻」的默认值 —— 不占时间轴
        return null
    }
    return time.hour * 60 + time.minute
}

/** 这天是不是今天（按本地日期比） */
internal fun isToday(millis: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val target = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return target == java.time.LocalDate.now(zone)
}

@Composable
private fun TimelineBlockCard(
    block: TimelineBlock,
    timeText: String,
    modifier: Modifier = Modifier,
) {
    val foreground = onColorFor(block.color)
    // Phase 15 P0-1.3：完成态 —— 划线 + 文字变淡 + 底色更淡，
    // 与 WeekCalendarView.TimelineBlockChip 的完成态保持同一套语义。
    val contentAlpha = if (block.isCompleted) 0.55f else 1f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.chip))
            .then(
                when {
                    block.isCompleted -> Modifier
                        .background(block.color.copy(alpha = if (block.filled) 0.5f else 0.08f))
                        .border(1.dp, block.color.copy(alpha = 0.20f), RoundedCornerShape(AppRadii.chip))
                    block.filled -> Modifier.background(block.color.copy(alpha = 0.85f))
                    // 13% 淡底。之前是 16% 底 + 32% 描边 ——
                    // 描边会把四边都描上，一屏几十个块全是框线，看着像表格而不是日程。
                    else -> Modifier.background(block.color.copy(alpha = 0.13f))
                }
            )
            .then(
                if (block.filled) {
                    // 实心底色上再画同色左条等于没画
                    Modifier
                } else {
                    // 3dp 实色左条 —— 用 drawBehind 画在内容层底下，
                    // 不占布局，也就不会把标题挤窄。
                    Modifier.drawBehind {
                        drawRect(
                            color = block.color,
                            size = Size(3.dp.toPx(), size.height),
                        )
                    }
                }
            )
            .padding(start = 8.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
    ) {
        Column {
            Text(
                text = block.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = foreground.copy(alpha = contentAlpha),
                textDecoration = if (block.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = 0.75f * contentAlpha),
                maxLines = 1,
            )
            if (block.subtitle != null) {
                Text(
                    text = block.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.7f * contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun EmptyTimelineHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "空着也是好事♪",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}
