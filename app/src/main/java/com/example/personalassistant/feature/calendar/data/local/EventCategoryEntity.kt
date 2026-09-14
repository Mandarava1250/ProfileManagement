package com.example.personalassistant.feature.calendar.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Phase 8 Sub-step 9：日历事件分类实体
 *
 * - 表名 event_categories
 * - 索引：sortOrder（用于「按 sortOrder 升序列出」）
 * - 与 finance categories 表结构对齐（name/color/iconKey/sortOrder/isDefault）
 */
@Entity(
    tableName = "event_categories",
    indices = [Index("sortOrder")],
)
@Serializable
data class EventCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: Int,

    /** Material Icon 名称（如 "Work" / "School" / "LocalActivity"），保留扩展性 */
    @ColumnInfo(name = "iconKey")
    val iconKey: String = "Label",

    /** 排序：数字越小越靠前 */
    @ColumnInfo(name = "sortOrder")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "isDefault")
    val isDefault: Boolean = false,
)
