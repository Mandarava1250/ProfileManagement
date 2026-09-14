package com.example.personalassistant.feature.calendar.presentation.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.core.utils.DateUtils
import com.example.personalassistant.feature.calendar.domain.model.Event

/**
 * Phase 8 收尾（plan 8final §2.1）通知 deeplink 专用 EventEditScreen（只读详情）
 *
 * **为什么只读**：
 * - 主编辑流（`EditEventSheet`）与 CalendarScreen 强耦合
 * - deeplink 直接进入保存流是危险动作（绕过用户确认）
 * - 本屏只展示事件基本信息 + "在日历中打开"按钮（跳到 Calendar 主屏，由用户主动编辑）
 *
 * 字段：
 * - TopAppBar：返回箭头 + 标题"事件详情"
 * - 标题（h1）
 * - 日期 + 时间（或"全天"）
 * - 分类（带色块）
 * - 备注（可空）
 * - 提醒分钟数（可空）
 * - "在日历中打开"按钮 → onOpenInCalendar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    eventId: Long,
    onBack: () -> Unit,
    onOpenInCalendar: () -> Unit,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val event by viewModel.event.collectAsStateWithLifecycle()
    val categories by viewModel.eventCategories.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("事件详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = loadState) {
                EventEditViewModel.LoadState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is EventEditViewModel.LoadState.NotFound -> {
                    NotFoundView(message = state.message, onBack = onBack)
                }
                EventEditViewModel.LoadState.Loaded -> {
                    val loaded = event
                    if (loaded != null) {
                        EventDetailContent(
                            event = loaded,
                            categoryName = categories
                                .firstOrNull { it.id == loaded.categoryId }
                                ?.name
                                ?: loaded.category,
                            onOpenInCalendar = onOpenInCalendar,
                        )
                    } else {
                        NotFoundView(message = "事件不存在", onBack = onBack)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventDetailContent(
    event: Event,
    categoryName: String?,
    onOpenInCalendar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题
        Surface(
            color = Color(event.color).copy(alpha = 0.12f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(event.color), CircleShape),
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // 日期
        InfoRow(
            icon = Icons.Filled.CalendarMonth,
            label = "日期",
            value = DateUtils.formatDate(event.date),
        )

        // 时间
        if (event.hasTimeRange && event.startTime != null && event.endTime != null) {
            InfoRow(
                icon = Icons.Filled.NotificationsActive,
                label = "时间",
                value = "${DateUtils.formatTime(event.startTime)} - ${DateUtils.formatTime(event.endTime)}",
            )
        } else {
            InfoRow(
                icon = Icons.Filled.CalendarMonth,
                label = "时间",
                value = "全天",
            )
        }

        // 分类
        if (!categoryName.isNullOrBlank()) {
            InfoRow(
                icon = Icons.Filled.Category,
                label = "分类",
                value = categoryName,
            )
        }

        // 备注
        event.note?.takeIf { it.isNotBlank() }?.let { note ->
            InfoRow(
                icon = Icons.Filled.Notes,
                label = "备注",
                value = note,
            )
        }

        // 提醒
        event.reminder?.let { minutes ->
            InfoRow(
                icon = Icons.Filled.NotificationsActive,
                label = "提醒",
                value = when {
                    minutes == 0 -> "准时提醒"
                    minutes < 60 -> "提前 $minutes 分钟"
                    minutes % 60 == 0 -> "提前 ${minutes / 60} 小时"
                    else -> "提前 ${minutes / 60} 小时 ${minutes % 60} 分钟"
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 在日历中打开按钮
        Button(
            onClick = onOpenInCalendar,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("在日历中打开")
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun NotFoundView(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(24.dp))
        Button(onClick = onBack) {
            Text("返回")
        }
    }
}