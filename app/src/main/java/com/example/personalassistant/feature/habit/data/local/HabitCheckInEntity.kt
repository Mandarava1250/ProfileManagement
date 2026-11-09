package com.example.personalassistant.feature.habit.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Phase 23：打卡记录实体
 *
 * - date: 当天 00:00 的毫秒时间戳（用 [com.example.personalassistant.feature.habit.domain.util.HabitDates] 生成）
 * - count: 当天累计次数
 *
 * ## 为什么一天只存一条
 *
 * 「今天打了 3 次」如果每次插一行，月度表 / 热力图就得做 SUM，
 * 而且「取消一次打卡」要删掉哪一条说不清。改成一天一条 + count 累加：
 * 统计就是 group by date，补卡 / 撤销都只动一行。
 *
 * `(habitId, date)` 唯一索引是这条约束的兜底 —— 重复插入会被数据库挡下来。
 */
@Entity(
    tableName = "habit_checkins",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["habitId", "date"], unique = true),
        Index("date"),
    ],
)
@Serializable
data class HabitCheckInEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "habitId")
    val habitId: Long,

    /** 当天 00:00 的毫秒时间戳 */
    @ColumnInfo(name = "date")
    val date: Long,

    /** 当天累计次数 */
    @ColumnInfo(name = "count")
    val count: Int = 0,

    /** 补卡备注 */
    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),
)
