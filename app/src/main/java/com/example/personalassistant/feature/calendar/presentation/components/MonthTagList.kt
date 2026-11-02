package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.personalassistant.core.ui.components.AppCard
import com.example.personalassistant.core.ui.theme.AppRadii
import com.example.personalassistant.core.ui.theme.AppSpacing
import com.example.personalassistant.feature.calendar.domain.model.DayDetail
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.timetable.domain.model.Course

/**
 * 月视图的标签列表（Phase 14）
 *
 * 月历网格只能画下「这一天有东西」，画不下「是什么」。
 * 所以网格下面接一排标签，把选中这一天的日程、任务、课程全列出来 ——
 * 横向滚动，一行一类，不换行。
 *
 * Phase 20：**日程 / 任务 / 课程三类都可点** —— 任务跳任务详情，课程跳课表详情。
 * 之前只有日程能点，任务和课程只是「摆在那儿」，用户看得到却摸不着。
 */
@Composable
fun MonthTagList(
    dayDetail: DayDetail?,
    modifier: Modifier = Modifier,
    onEventClick: (Event) -> Unit = {},
    onTaskClick: (Task) -> Unit = {},
    onCourseClick: (Course) -> Unit = {},
) {
    if (dayDetail == null) return
    val events = remember(dayDetail) { dayDetail.events }
    val tasks = remember(dayDetail) { dayDetail.tasks }
    val courses = remember(dayDetail) { dayDetail.courses }
    if (events.isEmpty() && tasks.isEmpty() && courses.isEmpty()) return

    AppCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(AppSpacing.sm),
    ) {
        if (events.isNotEmpty()) {
            TagRow(
                label = "日程",
                tags = events.map { event ->
                    TagChipData(
                        text = event.title,
                        color = Color(event.color),
                        onClick = { onEventClick(event) },
                    )
                },
            )
        }
        if (tasks.isNotEmpty()) {
            TagRow(
                label = "任务",
                tags = tasks.map { task ->
                    TagChipData(
                        text = task.title,
                        color = MaterialTheme.colorScheme.primary,
                        done = task.isCompleted,
                        onClick = { onTaskClick(task) },
                    )
                },
            )
        }
        if (courses.isNotEmpty()) {
            TagRow(
                label = "课程",
                tags = courses.map { course ->
                    TagChipData(
                        text = course.name,
                        color = Color(course.color),
                        onClick = { onCourseClick(course) },
                    )
                },
            )
        }
    }
}

/** 一行标签 */
private data class TagChipData(
    val text: String,
    val color: Color,
    val done: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun TagRow(
    label: String,
    tags: List<TagChipData>,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(tags) { tag ->
                TagChip(tag = tag)
            }
        }
    }
}

@Composable
private fun TagChip(tag: TagChipData) {
    val background = tag.color.copy(alpha = if (tag.done) 0.08f else 0.18f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppRadii.chip))
            .background(background)
            .border(1.dp, tag.color.copy(alpha = 0.35f), RoundedCornerShape(AppRadii.chip))
            .then(if (tag.onClick != null) Modifier.clickable { tag.onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tag.color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = tag.text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
    }
}
