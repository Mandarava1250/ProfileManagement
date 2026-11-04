package com.example.personalassistant.feature.finance.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personalassistant.feature.profile.data.local.AccountIds
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import kotlinx.serialization.Serializable

/**
 * 批次 C：零钱池实体（用户手动管理的一笔「随手钱」）
 *
 * ## 与 `daily_pools`（每日零钱池）的关系 —— 两张完全不同的表
 *
 * | | `daily_pools` | `coin_pools`（本表） |
 * |---|---|---|
 * | 语义 | 每日可支配额度的**系统快照** | 用户自己攒 / 花的一笔**零用钱** |
 * | 粒度 | 每天一行 | 每个账户一行 |
 * | 谁写 | 系统（每日刷新 + 记账扣减） | 用户手动存 / 取 |
 * | 能否为负 | 能（超支） | 能（欠账） |
 *
 * ⚠️ 命名容易混：老代码里的「零钱池」指的是 `daily_pools`（说明书 6.7 的特色功能），
 * 本表是**新**的、手动性质的钱包。UI 上必须用不同标题 + 不同图标区分（见 `CoinPoolCard`）。
 *
 * ## 为什么 accountId 建唯一索引 + 外键
 *
 * - 唯一：一账户一个零钱池，两次「存钱」是更新同一行，不是新增一行
 * - 外键 → `user_profiles.id`：删账户时连带清掉零钱池（ON DELETE CASCADE）
 *
 * ⚠️ 与记账域其它 4 张表不同 —— 它们**没有**真正的外键
 * （见 `UserProfileEntity` 的 KDoc：「数据库层没有对 4 张记账表建 FK」）。
 * 本表是第一条带真外键的记账域表，所以建表 DDL 必须与 Room 生成的形状**逐字一致**，
 * 否则 Room 运行期校验会抛 `Migration didn't properly handle: coin_pools`。
 */
@Entity(
    tableName = "coin_pools",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        )
    ],
    indices = [
        Index(value = ["accountId"], unique = true),
    ],
)
@Serializable
data class CoinPoolEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 所属账户 id（→ user_profiles.id，删账户连带删本行） */
    @ColumnInfo(name = "accountId", defaultValue = "1")
    val accountId: Long = AccountIds.DEFAULT_ACCOUNT_ID,

    /** 当前余额（可负 = 欠账） */
    @ColumnInfo(name = "balance")
    val balance: Double,

    /** 备注（可空） */
    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),
)
