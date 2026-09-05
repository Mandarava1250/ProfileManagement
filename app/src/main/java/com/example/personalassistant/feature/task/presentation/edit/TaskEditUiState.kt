package com.example.personalassistant.feature.task.presentation.edit

import com.example.personalassistant.feature.task.domain.model.RepeatRule
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.TaskCategory
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 任务编辑页 UI 状态
 *
 * - isNew：true 为新建模式（taskId < 0L），false 为编辑模式
 * - isLoading：编辑模式下初始加载任务内容
 * - isSaved：保存/删除成功后置 true，UI 层订阅此字段 popBackStack
 * - canSave：标题非空且不在加载中
 *
 * Phase 4a 扩展字段（接线暂留，UI 暴露下一轮）：
 * - category / categoryChips：分类选择
 * - repeatRule：重复规则（RepeatRule sealed class）
 * - reminderTime：提醒时间戳（null = 未设置）
 */
data class TaskEditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = true,
    val title: String = "",
    val titleError: String? = null,
    val description: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: Long? = null,
    val subTasks: List<SubTask> = emptyList(),
    val newSubTaskInput: String = "",
    val showDatePicker: Boolean = false,

    // ===== Phase 4a 新增 =====
    /** 分类文本（与 Task.category: String? 对齐） */
    val category: String? = null,
    /** 可选的预设分类（用于编辑 chip） */
    val categoryChips: List<TaskCategory> = TaskCategory.PRESETS,
    /** 重复规则 */
    val repeatRule: RepeatRule = RepeatRule.None,
    /** 提醒时间戳（null = 未设置） */
    val reminderTime: Long? = null,
    /** 是否显示重复规则选择 sheet */
    val showRepeatRuleSheet: Boolean = false,
    /** 是否显示分类选择 sheet */
    val showCategorySheet: Boolean = false,
    /** 是否显示提醒时间 picker */
    val showReminderPicker: Boolean = false,

    val isSaved: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean
        get() = !isLoading && title.isNotBlank()

    val dueDateLabel: String
        get() = dueDate?.let { formatDueDateLabel(it) } ?: "未设置"

    val reminderLabel: String
        get() = reminderTime?.let { formatReminderLabel(it) } ?: "未设置"

    val repeatRuleLabel: String
        get() = when (repeatRule) {
            RepeatRule.None -> "不重复"
            RepeatRule.Daily -> "每天"
            RepeatRule.Weekly -> "每周"
            RepeatRule.Monthly -> "每月"
            RepeatRule.Yearly -> "每年"
            is RepeatRule.CustomWeekdays -> "自定义（${repeatRule.days.size} 天）"
        }
}

private fun formatDueDateLabel(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.minusDays(1) -> "昨天"
        else -> if (date.year == today.year) {
            "${date.monthValue}/${date.dayOfMonth}"
        } else {
            "${date.year}/${date.monthValue}/${date.dayOfMonth}"
        }
    }
}

private fun formatReminderLabel(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    return "${dateTime.monthValue}/${dateTime.dayOfMonth} " +
        "${dateTime.hour.toString().padStart(2, '0')}:" +
        "${dateTime.minute.toString().padStart(2, '0')}"
}
