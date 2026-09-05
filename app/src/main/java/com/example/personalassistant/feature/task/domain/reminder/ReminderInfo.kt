package com.example.personalassistant.feature.task.domain.reminder

import java.time.Instant

/**
 * 单条提醒的全部信息 —— 调度器只看这个 data class。
 *
 * @param taskId 主键；同时也作为 reminder 的 stable id（便于覆盖/cancel）
 * @param title 通知标题（通常 = task.title）
 * @param body 通知正文（通常 = task.description）
 * @param triggerAt 触发瞬间（绝对时间，Instant；具体调度由实现决定）
 */
data class ReminderInfo(
    val taskId: Long,
    val title: String,
    val body: String,
    val triggerAt: Instant
)
