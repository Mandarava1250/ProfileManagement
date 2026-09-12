package com.example.personalassistant.feature.finance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personalassistant.feature.profile.data.local.AccountIds
import kotlinx.serialization.Serializable

/**
 * 交易记录实体（一条账目）
 *
 * - accountId: 所属账户（Phase 3 多账户隔离），指向 user_profiles.id
 * - type: 0=支出, 1=收入
 * - categoryId: 关联 CategoryEntity.id（同一账户内）
 * - date: 业务时间（用户记账那一刻），毫秒时间戳
 * - createdAt: 入库时间，用于审计
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("date"),
        Index("type"),
        Index("categoryId"),
        Index(value = ["date", "type"]),
        Index(value = ["type", "categoryId", "date"]),
        // Phase 3：记账页/统计查询全部以 accountId 为前导列
        Index(value = ["accountId", "date"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            // Phase 3：复合 FK —— 分类每账户一套，禁止跨账户引用
            parentColumns = ["accountId", "id"],
            childColumns = ["accountId", "categoryId"],
            onDelete = ForeignKey.RESTRICT, // 有交易记录的分类禁止删除
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
@Serializable
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 所属账户 id（Phase 3）；旧数据经 Migration_8_9 后统一为 1 */
    @ColumnInfo(name = "accountId", defaultValue = "1")
    val accountId: Long = AccountIds.DEFAULT_ACCOUNT_ID,

    @ColumnInfo(name = "amount")
    val amount: Double,

    /** 0=支出, 1=收入 */
    @ColumnInfo(name = "type")
    val type: Int,

    @ColumnInfo(name = "categoryId")
    val categoryId: Long,

    /** 业务时间（用户记账那一刻）*/
    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    /** 来源：「手动」「零钱池扣除」 */
    @ColumnInfo(name = "source")
    val source: String = "手动",

    /** 关联的图片 URI（可选，扫码记账场景）*/
    @ColumnInfo(name = "attachmentUri")
    val attachmentUri: String? = null
)
