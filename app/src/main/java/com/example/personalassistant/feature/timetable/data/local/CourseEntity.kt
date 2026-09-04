package com.example.personalassistant.feature.timetable.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 课程实体（一节具体的课）
 *
 * 设计要点：
 * - dayOfWeek: 1-7（周一=1，周日=7）
 * - startTime / endTime: 当天分钟数（0-1439），与日期无关，避免时区干扰
 * - semester: 关联 SemesterEntity.name（字符串外键，便于切换学期过滤）
 * - weeks: 周次规则，如 "1-16" 表示 1-16 周都上课；"1,3,5" 表示单周；"1-8,10-16" 表示 1-8 和 10-16 周
 * - reminderMinutes: 课前 N 分钟提醒，0 表示不提醒
 */
@Entity(
    tableName = "courses",
    indices = [
        Index("semester"),
        Index("dayOfWeek"),
        Index(value = ["semester", "dayOfWeek", "startTime"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["name"],
            childColumns = ["semester"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "teacher")
    val teacher: String = "",

    @ColumnInfo(name = "location")
    val location: String = "",

    /** 1=周一, 7=周日 */
    @ColumnInfo(name = "dayOfWeek")
    val dayOfWeek: Int,

    /** 当天分钟数，0-1439 */
    @ColumnInfo(name = "startTime")
    val startTime: Int,

    /** 当天分钟数，0-1439 */
    @ColumnInfo(name = "endTime")
    val endTime: Int,

    /** ARGB 颜色值，对应 CoursePalette 中的一个 */
    @ColumnInfo(name = "color")
    val color: Int,

    /** 关联 SemesterEntity.name */
    @ColumnInfo(name = "semester")
    val semester: String? = null,

    /** 周次规则：null 表示全学期都上课 */
    @ColumnInfo(name = "weeks")
    val weeks: String? = null,

    /** 课前提醒分钟数，0 表示不提醒 */
    @ColumnInfo(name = "reminderMinutes")
    val reminderMinutes: Int = 15,

    /** 备注 */
    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
)
