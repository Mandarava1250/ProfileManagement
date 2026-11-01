package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
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
import com.example.personalassistant.feature.calendar.domain.model.EventConflictInfo

/**
 * Phase 8 Sub-step 9 Step 4.2：冲突警告横幅（EditEventSheet 实时提示用）
 *
 * 涟宝解读：
 * - [info] 的 conflictingCourses 为空 → 短路不渲染（占位 0 像素）
 * - 否则渲染：
 *   - 顶部：⚠ 图标 + "与 N 门课程时间冲突" 标题
 *   - 列表：每行显示 课程名（加粗） + 时段（HH:mm-HH:mm） + @地点
 *
 * 配色：
 * - 底色：errorContainer 30% 透明（柔和）
 * - 边框：error 50% 透明
 * - 标题/图标：error
 * - 课程名：onSurface（保留正文可读性）
 * - 时段：onSurfaceVariant（次要文字色）
 *
 * 不展开/折叠：plan 决策 V1 简化版本，默认全部展示。
 */
@Composable
fun ConflictWarningBanner(
    info: EventConflictInfo,
    modifier: Modifier = Modifier,
) {
    if (info.conflictingCourses.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = "冲突警告",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "与 ${info.conflictingCourses.size} 门课程时间冲突",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        info.conflictingCourses.forEach { course ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.error),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val time = "${DateUtils.formatMinutesOfDay(course.startTime)} - " +
                        DateUtils.formatMinutesOfDay(course.endTime)
                    val subtitle = if (course.location.isNotBlank()) "$time @ ${course.location}" else time
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}