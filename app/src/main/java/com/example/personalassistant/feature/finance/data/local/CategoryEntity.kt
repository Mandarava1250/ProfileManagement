package com.example.personalassistant.feature.finance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personalassistant.feature.profile.data.local.AccountIds
import kotlinx.serialization.Serializable

/**
 * 分类实体（支出/收入分类）
 *
 * - accountId: 所属账户（Phase 3）—— 每个账户各自一套内置分类
 * - type: 0=支出分类, 1=收入分类
 * - isDefault: 是否系统内置（内置分类不可删除，仅可改名）
 * - iconName: Material Icon 名称（如 "Restaurant", "ShoppingCart"）
 */
@Entity(
    tableName = "categories",
    indices = [
        Index("type"),
        Index(value = ["type", "sortOrder"]),
        Index("accountId"),
        // Phase 3：transactions 的复合 FK 要求 parent 侧必须有 (accountId, id) 索引，
        // 否则 Room 建表时 FK 无法解析（SQLite 硬性要求）
        Index(value = ["accountId", "id"], unique = true)
    ]
)
@Serializable
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 所属账户 id（Phase 3）；内置分类每账户一份，故 id 会跨账户重复，必须靠它区分 */
    @ColumnInfo(name = "accountId", defaultValue = "1")
    val accountId: Long = AccountIds.DEFAULT_ACCOUNT_ID,

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
