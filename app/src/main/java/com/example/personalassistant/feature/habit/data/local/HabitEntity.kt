package com.example.personalassistant.feature.habit.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Phase 23：习惯实体
 *
 * - targetType: "DAILY" | "WEEKLY"（见 [com.example.personalassistant.feature.habit.domain.model.HabitTargetType]）
 * - targetCount: 目标次数（每日 N 次 / 每周 N 次）
 * - sortOrder: 列表顺序（值越小越靠前）
 * - isArchived: 归档后不再进「今日」列表，但历史打卡保留
 */
@Entity(
    tableName = "habits",
    indices = [
        Index("sortOrder"),
        Index("isArchived"),
    ],
)
@Serializable
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    /** 图标 key（如 "star"），UI 按 key 选图标 */
    @ColumnInfo(name = "iconKey")
    val iconKey: String = "star",

    /** ARGB 颜色 */
    @ColumnInfo(name = "color")
    val color: Int = 0xFFFF69B4.toInt(),

    @ColumnInfo(name = "targetType")
    val targetType: String = "DAILY",

    @ColumnInfo(name = "targetCount")
    val targetCount: Int = 1,

    /** 单位（「杯」「页」）；null = 不展示单位 */
    @ColumnInfo(name = "unit")
    val unit: String? = null,

    @ColumnInfo(name = "sortOrder")
    val sortOrder: Long = 0L,

    @ColumnInfo(name = "isArchived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
)
