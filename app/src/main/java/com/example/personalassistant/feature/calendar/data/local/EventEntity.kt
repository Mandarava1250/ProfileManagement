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

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
)
