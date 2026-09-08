package com.example.personalassistant.feature.calendar.domain.model

import com.example.personalassistant.feature.calendar.domain.recurrence.RecurrenceRule

/**
 * Phase 7c：日历事件（Domain 模型）
 *
 * 与 [com.example.personalassistant.feature.calendar.data.local.EventEntity] 一一对应，
 * 但去掉了 Room 注解 + 默认值简化，业务语义更清晰。
 *
 * - [date] 当天 00:00 时间戳（用作月视图分组键，与一天内的具体时刻无关）
 * - [isAllDay] = true 时忽略 [startTime] / [endTime]（整天事件）
 * - [startTime] / [endTime] 毫秒时间戳（含具体小时分钟），用于在日/周视图精确定位
 * - [reminder] 提前多少分钟提醒；null 表示不提醒
 * - [linkedTaskId] 关联任务（可选，UI 点击跳到任务详情）
 *
 * 设计取舍：
 * - [color] 用 Int 存 ARGB，UI 层用 Color(color) 还原
 * - [category] 留 String 字段（先用自由文本，未来 Phase 8 扩展为分类管理）
 * - Phase 8 Sub-step 9：[categoryId] 关联到 event_categories 表；[category] 旧字段保留只读兼容
 */
data class Event(
    val id: Long = 0L,
    val title: String,
    val date: Long,
    val isAllDay: Boolean = false,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val color: Int = DEFAULT_COLOR,
    val reminder: Int? = null,
    val note: String? = null,
    /**
     * 分类 ID（Phase 8 Sub-step 9）
     *
     * - null → 无分类（默认）
     * - 旧字段 [category] 保留只读兼容
     */
    val categoryId: Long? = null,
    val category: String? = null,
    val linkedTaskId: Long? = null,
    /**
     * Phase 8 Sub-step 9 Step 3：RRULE 重复规则
     *
     * - null → 不重复（单次事件）
     * - 非 null → 首次发生日期是 [date]，后续 occurrence 由 [com.example.personalassistant.feature.calendar.domain.recurrence.RecurrenceExpander] 按规则展开
     *
     * 涟宝设计取舍：
     * - DB 存的是 RFC 5545 字符串（[rrule]）；domain 层用 [RecurrenceRule] 对象（强类型）
     * - mapper 层做 toString ↔ RecurrenceRule 互转
     */
    val recurrence: RecurrenceRule? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(title.isNotBlank()) { "事件标题不能为空" }
        require(reminder == null || reminder >= 0) { "reminder 不能为负数" }
    }

    /**
     * 全天事件：[isAllDay] = true，或显式没有 startTime/endTime
     */
    val isEffectivelyAllDay: Boolean get() = isAllDay || (startTime == null && endTime == null)

    /** 是否有具体的起止时间（用于在日/周视图渲染为时间块） */
    val hasTimeRange: Boolean get() = !isEffectivelyAllDay && startTime != null && endTime != null

    companion object {
        /** 默认色：Material 3 primary（与 EventEntity 默认对齐） */
        const val DEFAULT_COLOR: Int = 0xFF6750A4.toInt()
    }
}