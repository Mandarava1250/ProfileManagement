package com.example.personalassistant.feature.calendar.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.calendar.domain.model.CalendarDisplayPreferences

/**
 * Phase 20.3：日历「显示设置」面板
 *
 * 四个开关：
 * - 课程：课表在日历里显不显示
 * - 清单项：清单项在日历里显不显示
 * - 已完成：已完成的清单项显不显示
 * - 习惯打卡：习惯打卡在日历里显不显示（复用习惯模块的开关）
 *
 * 开关的读写见 [com.example.personalassistant.feature.calendar.domain.repository.CalendarDisplayRepository]
 * 与 [com.example.personalassistant.feature.habit.domain.repository.HabitDisplayRepository]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDisplaySettingsSheet(
    preferences: CalendarDisplayPreferences,
    onShowCoursesChange: (Boolean) -> Unit,
    onShowTasksChange: (Boolean) -> Unit,
    onShowCompletedChange: (Boolean) -> Unit,
    onShowHabitsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "显示设置",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "决定日历里显示哪些内容。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DisplaySwitchRow(
                title = "课程",
                subtitle = "课表里的课显示在日历上",
                checked = preferences.showCourses,
                onCheckedChange = onShowCoursesChange,
            )
            DisplaySwitchRow(
                title = "清单项",
                subtitle = "清单里的待办显示在日历上",
                checked = preferences.showTasks,
                onCheckedChange = onShowTasksChange,
            )
            DisplaySwitchRow(
                title = "已完成",
                subtitle = "已完成的待办也显示在日历上",
                checked = preferences.showCompleted,
                onCheckedChange = onShowCompletedChange,
            )
            DisplaySwitchRow(
                title = "习惯打卡",
                subtitle = "习惯打卡与日历同屏（与习惯页共用同一个开关）",
                checked = preferences.showHabits,
                onCheckedChange = onShowHabitsChange,
            )
        }
    }
}

/** 一行「标题 + 说明 + 开关」 */
@Composable
private fun DisplaySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
