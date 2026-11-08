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
import androidx.compose.foundation.shape.CircleShape
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
    // Phase 26：列宽从「均分」改成「固定 60dp」，表头高固定 52dp。
    //
    // 网格那边已经改成 7 列 × 60dp 固定宽 + 横向滑动，表头必须用同一套基准，
    // 否则滑网格时星期标签停在原地，两边对不上。
    // 调用方把表头和网格放进**同一个** horizontalScroll，共用一个 ScrollState。
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val date = weekStart.plusDays(index.toLong())
            val isToday = date == today
            Column(
                modifier = Modifier
                    .width(TIMETABLE_COLUMN_WIDTH)
                    .height(52.dp)
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 今天：22dp 品牌蓝圆 + 白字
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent
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