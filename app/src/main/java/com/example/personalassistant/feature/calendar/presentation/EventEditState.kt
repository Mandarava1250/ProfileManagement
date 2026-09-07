package com.example.personalassistant.feature.calendar.presentation

import com.example.personalassistant.feature.calendar.domain.model.Event

/**
 * Phase 7c Sub-step 5：事件编辑状态（Compose 表单状态）
 *
 * ## 设计动机
 * - 直接用 [Event] 做表单状态会有 4 个问题：
 *   1. date 是当天 00:00 时间戳，UI 选择日期后需要重算（容易忘）
 *   2. startTime/endTime 是 epoch ms，UI 需要每次都做 zone 转换
 *   3. 没有"未填"概念（null 表示未填，但不能区分"已填但为 0"）
 *   4. createdAt / updatedAt / id 在编辑过程中不应该变化
 * - 所以单独建模一个表单状态，提交时再 map 回 [Event]
 *
 * ## 字段语义
 * - [originalId]：编辑已有事件时为该事件的 id；新增时为 0
 * - [date]：当天 00:00 时间戳（必填）
 * - [startHour] / [startMinute]：开始时间（null = 未填，代表无具体时刻/全天事件）
 * - [endHour] / [endMinute]：结束时间（null = 未填）
 * - [isAllDay]：是否全天（true 时忽略 startHour/startMinute/endHour/endMinute，UI 隐藏时间选择器）
 * - [hasTime]：是否有具体起止时间（false 时 isAllDay 永远 true）
 * - [reminderMinutes]：null = 不提醒；否则提前多少分钟提醒
 *
 * ## 提交时 map 规则
 * - 若 hasTime && isAllDay=false → 把 hour:minute 拼到 date 上得到 epoch ms
 * - 若 hasTime=false → startTime=null, endTime=null（自动全天）
 * - 若 reminderMinutes=null → reminder=null
 *
 * 设计取舍：颜色/分类默认值由 UI 层决定，不在这里硬编码。
 */
data class EventEditState(
    val originalId: Long = 0L,
    val title: String = "",
    val date: Long = 0L,
    val isAllDay: Boolean = true,
    val hasTime: Boolean = false,
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val category: String = "",
    val note: String = "",
    val reminderMinutes: Int? = null,
    val color: Int = Event.DEFAULT_COLOR,
    val linkedTaskId: Long? = null,
) {
    /** 当前是否为「新建」模式（originalId == 0） */
    val isNew: Boolean get() = originalId == 0L

    /**
     * 把表单状态还原为 [Event]
     *
     * 调用方负责补 createdAt / updatedAt：
     * - 新建：交给 repository 默认值（System.currentTimeMillis）
     * - 更新：保留原 Event 的 createdAt
     *
     * 时间字段转换：
     * - hasTime=false → startTime=null, endTime=null, isAllDay=true
     * - hasTime=true && isAllDay=false → 计算 epoch ms
     * - hasTime=true && isAllDay=true → 忽略 hour/minute，存 null
     */
    fun toEvent(
        createdAt: Long,
        updatedAt: Long,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Event {
        val finalIsAllDay = isAllDay || !hasTime
        val startMillis: Long?
        val endMillis: Long?
        if (hasTime && !isAllDay &&
            startHour != null && startMinute != null &&
            endHour != null && endMinute != null
        ) {
            val startLocal = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(date), zone,
            )
                .withHour(startHour)
                .withMinute(startMinute)
                .withSecond(0)
                .withNano(0)
            val endLocal = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(date), zone,
            )
                .withHour(endHour)
                .withMinute(endMinute)
                .withSecond(0)
                .withNano(0)
            startMillis = startLocal.atZone(zone).toInstant().toEpochMilli()
            endMillis = endLocal.atZone(zone).toInstant().toEpochMilli()
        } else {
            startMillis = null
            endMillis = null
        }
        return Event(
            id = originalId,
            title = title.trim(),
            date = date,
            isAllDay = finalIsAllDay,
            startTime = startMillis,
            endTime = endMillis,
            color = color,
            reminder = reminderMinutes,
            note = note.ifBlank { null },
            category = category.ifBlank { null },
            linkedTaskId = linkedTaskId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        /**
         * 从已有 [Event] 构建编辑状态（编辑模式入口）
         *
         * - date 沿用（已是当天 00:00）
         * - 若有 startTime/endTime，转换为本地的 hour/minute
         * - hasTime 根据 isEffectivelyAllDay 推断
         */
        fun fromEvent(event: Event, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): EventEditState {
            val effective = event.isEffectivelyAllDay
            val start: java.time.LocalDateTime? = event.startTime?.let {
                java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), zone)
            }
            val end: java.time.LocalDateTime? = event.endTime?.let {
                java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), zone)
            }
            return EventEditState(
                originalId = event.id,
                title = event.title,
                date = event.date,
                isAllDay = event.isAllDay,
                hasTime = !effective,
                startHour = start?.hour,
                startMinute = start?.minute,
                endHour = end?.hour,
                endMinute = end?.minute,
                category = event.category.orEmpty(),
                note = event.note.orEmpty(),
                reminderMinutes = event.reminder,
                color = event.color,
                linkedTaskId = event.linkedTaskId,
            )
        }

        /**
         * 新建模式入口：给定日期（当天 00:00 时间戳）+ 默认时长
         */
        fun newOnDate(
            date: Long,
            startHour: Int = 9,
            startMinute: Int = 0,
            endHour: Int = 10,
            endMinute: Int = 0,
        ): EventEditState = EventEditState(
            originalId = 0L,
            title = "",
            date = date,
            isAllDay = false,
            hasTime = true,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
        )
    }
}
