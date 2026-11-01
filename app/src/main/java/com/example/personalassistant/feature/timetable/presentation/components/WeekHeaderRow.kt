package com.example.personalassistant.feature.timetable.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * 周导航头：左箭头 + "第 N 周" + 右箭头 + "回到本周"
 */
@Composable
fun WeekHeaderRow(
    weekNumber: Int,
    isCurrentWeek: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToCurrent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevWeek) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一周")
        }

        Spacer(Modifier.width(4.dp))

        Text(
            text = "第 $weekNumber 周",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        IconButton(onClick = onNextWeek) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "下一周")
        }

        if (!isCurrentWeek) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onJumpToCurrent) {
                Icon(Icons.Rounded.Today, contentDescription = "回到本周")
            }
        }
    }
}

/**
 * 周一到周日的日期头，高亮今天
 */
@Composable
fun DayHeaderRow(
    weekStart: LocalDate,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
) {
    val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    // ⚠️ 这里刻意**不写** fillMaxWidth()。
    // 调用方（TimetableScreen）把它放进 `Row { Spacer(TIMETABLE_AXIS_WIDTH); DayHeaderRow(weight=1f) }`，
    // fillMaxWidth 会强行撑满父 Row、吃掉 weight 算出来的宽度，Spacer 之外的部分就错位了。
    // 单独使用（无 weight）时行为不变：weight 默认 0 时 Row 会给它剩余全部宽度。
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val date = weekStart.plusDays(index.toLong())
            val isToday = date == today
            val containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            val textColor = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(containerColor)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                    )
                    Text(
                        text = "${date.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                    )
                }
            }
        }
    }
}