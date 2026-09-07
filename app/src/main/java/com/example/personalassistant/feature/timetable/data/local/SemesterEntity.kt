package com.example.personalassistant.feature.timetable.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 学期实体
 *
 * name 作为业务主键（如 "2024-2025-1"），方便跨表引用
 * isCurrent 唯一为 true 表示当前学期（应用层维护一致性）
 */
@Entity(
    tableName = "semesters",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["isCurrent"])
    ]
)
@Serializable
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    /** 学期开始日期（当天 00:00 时间戳）*/
    @ColumnInfo(name = "startDate")
    val startDate: Long,

    /** 学期结束日期（当天 00:00 时间戳）*/
    @ColumnInfo(name = "endDate")
    val endDate: Long,

    /** 是否当前学期 */
    @ColumnInfo(name = "isCurrent")
    val isCurrent: Boolean = false,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
