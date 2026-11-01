package com.example.personalassistant.feature.calendar.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 日历事件实体
 *
 * - date: 当天 00:00 时间戳（用做日历分组键）
 * - isAllDay=true 时忽略 startTime/endTime
 * - reminder: 提前多少分钟提醒
 */
@Entity(
    tableName = "events",
    indices = [
        Index("date"),
        Index(value = ["date", "startTime"])
    ]
)
@Serializable
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    /** 当天 00:00 时间戳（用作日历月视图分组）*/
    @ColumnInfo(name = "date")
    val date: Long,

    /**
     * 关联分类 ID（Phase 8 Sub-step 9 新增）
     *
     * - null 表示未分类
     * - 外键 reference：event_categories.id（ON DELETE SET NULL）
     * - 旧字段 [category] 保留只读兼容（sub-step 9 之前的数据迁移用，后续可弃用）
     */
    @ColumnInfo(name = "categoryId", index = true)
    val categoryId: Long? = null,

    @ColumnInfo(name = "isAllDay")
    val isAllDay: Boolean = false,

    /** 开始时间（毫秒时间戳）*/
    @ColumnInfo(name = "startTime")
    val startTime: Long? = null,

    /** 结束时间（毫秒时间戳）*/
    @ColumnInfo(name = "endTime")
    val endTime: Long? = null,

    /** ARGB 颜色 */
    @ColumnInfo(name = "color")
    val color: Int = 0xFF6750A4.toInt(),

    /** 提前多少分钟提醒 */
    @ColumnInfo(name = "reminder")
    val reminder: Int? = null,

    @ColumnInfo(name = "note")
    val note: String? = null,

    /** 用户分类：「会议」「生日」「纪念日」等 */
    @ColumnInfo(name = "category")
    val category: String? = null,

    /** 关联的任务 ID（可选，日历里点事件跳到任务详情）*/
    @ColumnInfo(name = "linkedTaskId")
    val linkedTaskId: Long? = null,

    /**
     * Phase 8 Sub-step 9 Step 3：RRULE 重复规则（RFC 5545 字符串）
     *
     * - null → 不重复（单次事件）
     * - 非 null → 存的是 RRULE 序列化字符串（如 "FREQ=WEEKLY;BYDAY=MO,WE"）
     * - 首次发生日期用 [date] 字段，展开逻辑由 [com.example.personalassistant.feature.calendar.domain.recurrence.RecurrenceExpander] 处理
     */
    @ColumnInfo(name = "rrule")
    val rrule: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
)
