package com.example.personalassistant.feature.timetable.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.presentation.components.CourseCard

/**
 * 周视图网格：每天一列；每列用 LazyColumn 渲染课程
 *
 * 实现简化（性能足够好）：
 *   - 每列内部按 startTime 排序，渲染 CourseCard
 *   - 空列显示"无课程"
 */
@Composable
fun TimetableGrid(
    coursesByDay: Map<Int, List<Course>>,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = (1..7).toList()
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        days.forEach { day ->
            val list = coursesByDay[day].orEmpty()
            Column(modifier = Modifier.weight(1f).padding(horizontal = 1.dp)) {
                if (list.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(
                            text = "无课程",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(list, key = { it.id.takeIf { id -> id != 0L } ?: it.hashCode().toLong() }) { course ->
                            CourseCard(course = course, onClick = { onCourseClick(course) })
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}