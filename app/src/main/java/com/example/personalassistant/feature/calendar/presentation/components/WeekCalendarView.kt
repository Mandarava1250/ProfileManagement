package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.core.ui.theme.LocalAppColors
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.util.CalendarTaskFilter
import com.example.personalassistant.feature.calendar.domain.util.CalendarTimeRange
import com.example.personalassistant.feature.calendar.domain.util.sessionsOn
import com.example.personalassistant.feature.calendar.presentation.WeekDayColumn
import com.example.personalassistant.feature.calendar.presentation.WeekTimeline
import com.example.personalassistant.feature.task.domain.model.Task
import java.time.Instant
import java.time.ZoneId

/** 左边时间刻度列的宽度 —— 全天标签区也要留出这么多，两边才对得齐 */
private val GutterWidth = 42.dp

/** 一小时占多高 */
private val HourHeight = 46.dp

/**
 * 周视图 = 整周 7 列并排的时间轴
 *
 * 伙伴给的草案（Demo_Week.html）长这样，从上到下：
 *
 * 1. **星期头部**：周一~周日横排，上面是「一二三四五六日」，下面是日期圆圈，选中的那天是实心色圆
 * 2. **全天标签区**：没有时刻的任务和全天日程，**按天分列**，位置跟下面的时间轴一一对齐
 * 3. **时间轴网格**：左边一列时间刻度，右边 7 列（周一~周日）并排，每列放各自的课程、事件和任务
 *    ——「现在」是一条红线，画在今天那一列上，左边带个小圆点
 *
 * 一条铁律：**每一件东西都待在自己那一天的列里**。
 * 任务堆在一个不分日期的地方，用户就分不清它是周几的（Phase 14 修的就是这个）。
 */
@Composable
fun WeekCalendarView(
    timeline: WeekTimeline,
    selectedDate: Long?,
    onDateClick: (Long) -> Unit,
    onToggleTask: (Task, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    /**
     * 时间轴的滚动条。
     *
     * 传 null = 时间轴自己滚（周视图独占一屏时用）；
     * 传外层的 = 时间轴跟着整页一起滚 —— 周视图要跟下面的「整周清单」
     * 共用一个滚动条时用，不然两个滚动区叠在一起，滚轮打架。
     */
    timeGridScrollState: ScrollState? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        // Phase 15 P1-2.2：三段之间留白 —— 星期头 / 全天标签区 / 时间轴
        // 挤在一起时看不出哪里是哪里
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        // ===== 1. 星期头部 =====
        WeekDayHeader(
            days = timeline.days,
            selectedDate = selectedDate,
            onDateClick = onDateClick,
        )

        // ===== 2. 全天标签区（按天分列）=====
        AllDayRow(
            days = timeline.days,
            onToggleTask = onToggleTask,
        )

        // ===== 3. 时间轴 7 列 =====
        Box(
            modifier = if (timeGridScrollState != null) {
                // 交给外层滚：这里不能再抢 weight，否则整页高度算不出来
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            },
        ) {
            WeekTimeGrid(
                days = timeline.days,
                onToggleTask = onToggleTask,
                externalScrollState = timeGridScrollState,
            )
        }
    }
}

// =====================================================================
// 1. 星期头部
// =====================================================================

@Composable
private fun WeekDayHeader(
    days: List<WeekDayColumn>,
    selectedDate: Long?,
    onDateClick: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = AppSpacing.sm),
    ) {
        days.forEach { day ->
            val selected = selectedDate != null && day.date == selectedDate
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppSpacing.sm))
                    .clickable { onDateClick(day.date) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = weekDayLabel(day.dayOfWeek),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = dayOfMonth(day.date),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

// =====================================================================
// 2. 全天标签区 —— 按天分列，跟时间轴对齐
// =====================================================================

/**
 * 没有时刻的任务和全天日程摆在这。
 *
 * 关键在于**按天分列**：每个标签就在自己那一天的列里，
 * 跟下面的时间轴对齐，一眼就知道这条是周几的。
 */
@Composable
private fun AllDayRow(
    days: List<WeekDayColumn>,
    onToggleTask: (Task, Boolean) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val itemsByDay = remember(days, primary) { days.map { it.allDayItems(primary) } }
    if (itemsByDay.all { it.isEmpty() }) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 84.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // 左边留出刻度列的宽度，跟下面的时间轴列对齐
        Spacer(Modifier.width(GutterWidth))
        itemsByDay.forEach { items ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 1.dp),
                // Phase 15 P1-2.2：列间距 2dp -> 4dp，7 列挤在一起看不清哪列是哪列
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { item ->
                    AllDayTag(item = item, onToggleTask = onToggleTask)
                }
            }
        }
    }
}

@Composable
private fun AllDayTag(
    item: AllDayItem,
    onToggleTask: (Task, Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(item.color.copy(alpha = if (item.isCompleted) 0.10f else 0.18f))
            .then(
                if (item.task != null) {
                    // 已完成的点一下是「取消完成」，不是「再完成一次」
                    Modifier.clickable { onToggleTask(item.task, !item.task.isCompleted) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        Text(
            text = item.text,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Phase 14：已完成的任务继续留在日历里，但划线 + 淡化，一眼分得清
            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
            color = if (item.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private data class AllDayItem(
    val text: String,
    val color: Color,
    /** 是任务的话，点一下就能勾完成 */
    val task: Task?,
    val isCompleted: Boolean = false,
)

/**
 * 这一天「没有时刻」的条目。
 *
 * 任务带时刻的去时间轴，这里只放两种：
 * - 任务没填截止时刻（或者只填了日期）
 * - 全天日程
 *
 * Phase 20.2：**带时间段的清单项不进这里** —— 它在时间轴上（跨午夜的占两天）。
 */
private fun WeekDayColumn.allDayItems(primary: Color): List<AllDayItem> {
    val result = mutableListOf<AllDayItem>()
    tasks.forEach { task ->
        if (CalendarTaskFilter.isTimed(task)) return@forEach
        val minute = task.dueDate?.let { minuteOfDay(it) }
        if (minute == null) {
            result += AllDayItem(
                text = task.title,
                color = primary,
                task = task,
                isCompleted = task.isCompleted,
            )
        }
    }
    events.filter { it.isEffectivelyAllDay }.forEach { event ->
        result += AllDayItem(text = event.title, color = Color(event.color), task = null)
    }
    // Phase 24B：习惯没有时刻，也进「全天标签区」
    // isMet 复用成「已达标」→ 走删除线 + 淡化的既有渲染
    habits.forEach { habit ->
        result += AllDayItem(
            text = habit.name,
            color = Color(habit.color),
            task = null,
            isCompleted = habit.isMet,
        )
    }
    return result
}

// =====================================================================
// 3. 时间轴 7 列
// =====================================================================

@Composable
private fun WeekTimeGrid(
    days: List<WeekDayColumn>,
    onToggleTask: (Task, Boolean) -> Unit,
    externalScrollState: ScrollState? = null,
) {
    val ownScrollState = rememberScrollState()
    // 外部传了就跟整页一起滚；没传就自己滚
    val scrollState = externalScrollState ?: ownScrollState
    val density = LocalDensity.current
    val nowMinute = remember { DateUtils.currentMinuteOfDay() }
    val todayIndex = remember(days) { days.indexOfFirst { isToday(it.date) } }
    val primary = MaterialTheme.colorScheme.primary

    // 打开就落在「现在」附近 —— 24 小时的轴从 00:00 划到上午十点要好几屏
    LaunchedEffect(todayIndex) {
        if (todayIndex >= 0) {
            val target = with(density) {
                (nowMinute / 60f * HourHeight.toPx() - 80.dp.toPx()).coerceAtLeast(0f)
            }
            scrollState.scrollTo(target.toInt())
        }
    }

    Column(
        modifier = if (externalScrollState != null) {
            // 外层已经在滚了，这里再加一个 verticalScroll 会打架
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .verticalScroll(ownScrollState)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HourHeight * 24),
        ) {
            // 左边：时间刻度
            Column(
                modifier = Modifier
                    .width(GutterWidth)
                    .fillMaxHeight(),
            ) {
                repeat(24) { hour ->
                    Box(
                        modifier = Modifier.height(HourHeight),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Text(
                            text = DateUtils.formatMinutesOfDay(hour * 60),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }

            // 右边：7 列
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                days.forEachIndexed { index, day ->
                    WeekDayColumnView(
                        day = day,
                        showNowLine = index == todayIndex,
                        nowMinute = nowMinute,
                        primary = primary,
                        onToggleTask = onToggleTask,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 周一~周日里的一列。
 *
 * 列窄（360dp 屏上每列 40dp 出头），所以块里的字很小 —— 能认出是「高等数学」就行，
 * 想读全名点进日视图。
 */
@Composable
private fun WeekDayColumnView(
    day: WeekDayColumn,
    showNowLine: Boolean,
    nowMinute: Int,
    primary: Color,
    onToggleTask: (Task, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(day, primary) { day.buildWeekBlocks(primary) }
    // 同一天里时间撞上的块平分成几列并排，不然会盖住
    val placed = remember(blocks) { blocks.assignColumns() }
    // 按列分桶：placed 不变时桶也不变，别在 repeat(columnCount) 里反复全表扫描
    val blocksByColumn = remember(placed) { placed.groupBy { it.second } }
    val columnCount = placed.maxOfOrNull { it.second + 1 } ?: 1

    // 横线颜色在父层取一次：主题切换时父层重组 → 新颜色传下去
    val hourLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Box(modifier = modifier.fillMaxHeight()) {
        // 整点横线（独立子组件：颜色没变时这 24 个 Box 整段跳过）
        HourGridLines(hourLineColor)

        // 块
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HourHeight * 24),
        ) {
            repeat(columnCount) { col ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    blocksByColumn[col].orEmpty().forEach { (block) ->
                        TimelineBlockChip(
                            block = block,
                            onToggleTask = onToggleTask,
                        )
                    }
                }
            }
        }

        // 「现在」线：只画在今天那一列，左边带个小圆点。
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
                    .offset(y = HourHeight * (nowMinute / 60f)),
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(nowInk)
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = HourHeight * (nowMinute / 60f) - 3.dp),
            )
        }
    }
}

/**
 * 一列里的 24 条整点横线。
 *
 * 单独抽成一个子 Composable：入参只有一个 [Color]（稳定类型），
 * 父组件重组而颜色没变时，这 24 个 Box 整段跳过、不必重建。
 *
 * 布局与原先写在 [WeekDayColumnView] 里完全一致：
 * 同样 24 个 1dp 高的 Box、同样 TopStart 对齐、同样按 [HourHeight] 逐小时下移。
 */
@Composable
private fun BoxScope.HourGridLines(color: Color) {
    repeat(24) { hour ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(color)
                .align(Alignment.TopStart)
                .offset(y = HourHeight * hour),
        )
    }
}

@Composable
private fun TimelineBlockChip(
    block: WeekBlock,
    onToggleTask: (Task, Boolean) -> Unit,
) {
    val top = HourHeight * (block.startMinute / 60f)
    val height = HourHeight * ((block.endMinute - block.startMinute) / 60f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
            .offset(y = top)
            .height(height - 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(block.color.copy(alpha = if (block.isCompleted) 0.10f else 0.18f))
            .then(
                if (block.task != null) {
                    // 已完成的点一下是「取消完成」
                    Modifier.clickable { onToggleTask(block.task, !block.task.isCompleted) }
                } else {
                    Modifier
                }
            ),
    ) {
        // 左侧 3dp 语义色条
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(block.color),
        )
        Text(
            text = block.title,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Phase 14：已完成的任务留在日历里，但划线 + 淡化，一眼分得清
            textDecoration = if (block.isCompleted) TextDecoration.LineThrough else null,
            color = if (block.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(start = 5.dp, top = 2.dp, end = 2.dp),
        )
        // Phase 20.7：跨午夜的块标一下「跨天」
        if (block.crossesMidnight) {
            Text(
                text = "跨天",
                fontSize = 8.sp,
                lineHeight = 9.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
    }
}

/** 一列里要画哪些块：课程 + 带时刻的事件 + 带时刻的清单项 */
private fun WeekDayColumn.buildWeekBlocks(primary: Color): List<WeekBlock> {
    val courseBlocks = courses.flatMap { course ->
        // Phase 28 P6：一个课时 = 一个块 —— 一门课在这一天有几个课时就画几个块
        course.sessionsOn(course.dayOfWeek).map { session ->
            WeekBlock(
                title = course.name,
                startMinute = session.startTime,
                endMinute = session.endTime,
                color = Color(course.color),
                task = null,
            )
        }
    }
    val eventBlocks = events.mapNotNull { event ->
        if (event.isEffectivelyAllDay) return@mapNotNull null
        // Phase 15：startTime / endTime 是 epoch 毫秒时间戳（Event.kt:13），
        // 之前 `?.toInt()` 截断当分钟数用 → 事件在周视图上的位置全是错的。
        //
        // Phase 20.7：统一走「跨天条」切段 —— 跨午夜的事件在第二天也能看到它的尾巴，
        // 不再被截断成 1 小时。
        val startMillis = event.startTime ?: return@mapNotNull null
        val endMillis = event.endTime ?: startMillis
        val segment = CalendarTimeRange.segmentOn(startMillis, endMillis, date)
            ?: return@mapNotNull null
        WeekBlock(
            title = event.title,
            startMinute = segment.startMinute,
            endMinute = segment.endMinute,
            color = Color(event.color),
            task = null,
            crossesMidnight = segment.crossesMidnight,
        )
    }
    val taskBlocks = tasks.mapNotNull { task ->
        // Phase 20.2（过滤点 3）+ 20.4：带时间段的清单项按**真实时段**占位，
        // 与课程、事件排在同一条轴上；没填时间段的才按截止时刻占 1 小时。
        if (CalendarTaskFilter.isTimed(task)) {
            val segment = CalendarTaskFilter.segmentOn(task, date) ?: return@mapNotNull null
            return@mapNotNull WeekBlock(
                title = task.title,
                startMinute = segment.startMinute,
                endMinute = segment.endMinute,
                color = primary,
                task = task,
                isCompleted = task.isCompleted,
                crossesMidnight = segment.crossesMidnight,
            )
        }
        val due = task.dueDate ?: return@mapNotNull null
        // 只填了日期没填时刻的任务不进时间轴，它待在全天标签区
        val minute = minuteOfDay(due) ?: return@mapNotNull null
        WeekBlock(
            title = task.title,
            startMinute = minute,
            // 任务没有时长概念，统一按 1 小时占位
            endMinute = minute + 60,
            color = primary,
            task = task,
            isCompleted = task.isCompleted,
        )
    }
    return (courseBlocks + eventBlocks + taskBlocks).sortedBy { it.startMinute }
}

/**
 * 同一天里时间重叠的块平分成几列并排。
 *
 * 贪心：找一个已经结束的列放进去，没有就新开一列。
 */
private fun List<WeekBlock>.assignColumns(): List<Pair<WeekBlock, Int>> {
    val result = mutableListOf<Pair<WeekBlock, Int>>()
    val columnEnds = mutableListOf<Int>()
    forEach { block ->
        var target = -1
        for (i in columnEnds.indices) {
            if (columnEnds[i] <= block.startMinute) {
                target = i
                break
            }
        }
        if (target < 0) {
            columnEnds += block.endMinute
            target = columnEnds.size - 1
        } else {
            columnEnds[target] = block.endMinute
        }
        result += block to target
    }
    return result
}

private data class WeekBlock(
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val color: Color,
    /** 是任务的话，点一下就能勾完成 */
    val task: Task?,
    /** 已完成：划线 + 淡化 */
    val isCompleted: Boolean = false,
    /** Phase 20.7：属于跨午夜的时段（这一截画到 24:00 / 从 00:00 开始） */
    val crossesMidnight: Boolean = false,
)

// =====================================================================
// 小工具
// =====================================================================

/** dayOfWeek（1 = 周一 … 7 = 周日）→ 单字 */
private fun weekDayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}

private fun dayOfMonth(dateMillis: Long): String {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(dateMillis).atZone(zone).toLocalDate().dayOfMonth.toString()
}
