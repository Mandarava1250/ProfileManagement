package com.example.personalassistant.feature.finance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分类实体（支出/收入分类）
 *
 * - type: 0=支出分类, 1=收入分类
 * - isDefault: 是否系统内置（内置分类不可删除，仅可改名）
 * - iconName: Material Icon 名称（如 "Restaurant", "ShoppingCart"）
 */
@Entity(
    tableName = "categories",
    indices = [
        Index("type"),
        Index(value = ["type", "sortOrder"])
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    /** 0=支出, 1=收入 */
    @ColumnInfo(name = "type")
    val type: Int,

    /** Material Icon 名称 */
    @ColumnInfo(name = "iconName")
    val iconName: String = "Category",

    @ColumnInfo(name = "color")
    val color: Int = 0xFF6750A4.toInt(),

    @ColumnInfo(name = "isDefault")
    val isDefault: Boolean = false,

    /** 排序顺序，数字越小越靠前 */
    @ColumnInfo(name = "sortOrder")
    val sortOrder: Int = 0
)

/**
 * 统计查询结果：分类汇总
 */
data class CategorySum(
    val categoryId: Long,
    val total: Double
)
