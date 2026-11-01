package com.example.personalassistant.core.holiday.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Phase 8 Sub-step 7：节假日实体
 *
 * 数据库表 `holidays`。
 *
 * 字段：
 * - [id]           自增主键
 * - [dateEpochDay] 日期（自 1970-01-01 起的天数；LocalDate.toEpochDay() 的反函数）
 * - [name]         节日名（"国庆节"、"春节"）
 * - [type]         节假日类型
 * - [year]         年份（冗余字段，方便「按年查询」与 UI 展示；查询时仍以 dateEpochDay 为准）
 * - [note]         可选备注（"农历正月初一"、"调休上班"）
 *
 * 索引：
 * - UNIQUE(dateEpochDay, type)：同一天同一类型唯一；允许出现"WORKDAY + OBSERVANCE" 同日并存
 * - (year)：按年扫描
 */
@Entity(
    tableName = "holidays",
    indices = [
        Index(value = ["dateEpochDay", "type"], unique = true),
        Index("year"),
    ],
)
@Serializable
data class HolidayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "dateEpochDay")
    val dateEpochDay: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: HolidayType,

    @ColumnInfo(name = "year")
    val year: Int,

    @ColumnInfo(name = "note")
    val note: String? = null,
)