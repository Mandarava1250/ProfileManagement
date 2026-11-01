package com.example.personalassistant.core.database

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Room 类型转换器
 *
 * 当前所有日期相关字段都使用 Long 时间戳存储（避免时区/序列化问题），
 * 本文件保留 LocalDate/LocalDateTime 转换器以便 Phase 2+ 灵活选用
 */
class Converters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? =
        date?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    @TypeConverter
    fun toLocalDate(timestamp: Long?): LocalDate? =
        timestamp?.let {
            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        }

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): Long? =
        dateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    @TypeConverter
    fun toLocalDateTime(timestamp: Long?): LocalDateTime? =
        timestamp?.let {
            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
        }
}
