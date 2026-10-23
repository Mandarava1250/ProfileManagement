package com.example.personalassistant.feature.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.personalassistant.feature.settings.HolidayPreferencesUi

/**
 * Phase 13：日历分组的长期设置
 *
 * 这一组原本叫「智能提醒」，混在旧设置页里没说明白归属。
 * 它管的是**日历/提醒**这一侧的事 —— 重复任务遇到法定节假日要不要避让、
 * 每天几点提前告诉你明天有调休 —— 所以归到「日历」分组。
 *
 * 节假日避让同时影响任务侧的重复规则，但触发源是日历数据，留在日历这边更顺。
 */
@Composable
fun CalendarSection(
    holidayState: HolidayPreferencesUi,
    onAvoidEnabledChange: (Boolean) -> Unit,
    onEveningEnabledChange: (Boolean) -> Unit,
    onPickEveningTime: () -> Unit,
    onSyncHolidays: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModuleHeader(
            icon = Icons.Filled.CalendarMonth,
            title = "日历",
            subtitle = "节假日避让与提前提醒",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                    Spacer8()
                    Text("智能提醒", style = MaterialTheme.typography.titleMedium)
                }
                Spacer8()
                Text(
                    text = "节假日避让 + 每日 21:00 提前通知明天/后天的节假日与调休。",
                    style = MaterialTheme.typography.bodySmall,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // 节假日避让总开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("节假日避让", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "重复任务遇到法定节假日按各自策略避让",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = holidayState.avoidEnabled,
                        onCheckedChange = onAvoidEnabledChange,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 提前通知开关 + 时间选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("每日提前通知", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (holidayState.eveningEnabled) {
                                "每天 ${holidayState.eveningTimeLabel} 提醒明日节假日"
                            } else {
                                "已关闭,不发送提前通知"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = holidayState.eveningEnabled,
                        onCheckedChange = onEveningEnabledChange,
                    )
                }

                if (holidayState.eveningEnabled) {
                    Spacer8()
                    OutlinedButton(
                        onClick = onPickEveningTime,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("调整时间（当前 ${holidayState.eveningTimeLabel}）")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // 数据范围 + 同步入口
                Text(
                    text = if (holidayState.maxYear != null) {
                        "节假日数据覆盖：${holidayState.maxYear} 年及之前"
                    } else {
                        "节假日数据为空,请联系开发者"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer8()
                OutlinedButton(
                    onClick = onSyncHolidays,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                    Spacer8()
                    Text("立即同步节假日")
                }
            }
        }
    }
}
