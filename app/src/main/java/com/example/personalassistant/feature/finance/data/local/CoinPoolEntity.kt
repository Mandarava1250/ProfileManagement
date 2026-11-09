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
 * 批次 C：零钱池实体
 *
 * ## 批次 D：语义从「用户手动存取」改成「每日自动结算」
 *
 * 用户新要求：「零钱池 = 今日剩余在今日结束后自动存入，用户无法自行更改余额」。
 * 所以这张表现在是**系统单方面写**的：
 * - 用户没有任何入口改余额（`AdjustCoinPoolUseCase` / `SetCoinPoolBalanceUseCase` 已删）
 * - 余额 = 历次日切结算额之累加（可负）
 *
 * ## 与 `daily_pools`（说明书 6.7 的「每日零钱池」）的关系 —— 三张不同的表
 *
 * | | `daily_pools` | `coin_pools`（本表） | `coin_pool_settlements` |
 * |---|---|---|---|
 * | 语义 | 每日可支配额度的**系统快照** | 累计余额（当前值） | 每日结算额（流水） |
 * | 粒度 | 每天一行 | 每个账户一行 | 每天一行 |
 * | 谁写 | 系统（每日刷新 + 记账扣减） | 系统（日切结算累加） | 系统（日切结算追加） |
 * | 能否为负 | 能（超支） | 能（累计超支） | 能（当天超支） |
 *
 * ⚠️ 命名容易混：老代码里的「零钱池」指的是 `daily_pools`（说明书 6.7 的特色功能），
 * 本表是**新**的、累计结余性质的钱包。UI 上必须用不同标题 + 不同图标区分（见 `CoinPoolCard`）。
 *
 * ## 为什么 accountId 建唯一索引 + 外键
 *
 * - 唯一：一账户一个零钱池，每次结算是更新同一行，不是新增一行
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

    /** 当前余额（可负 = 累计欠账；= 历次结算额之累加） */
    @ColumnInfo(name = "balance")
    val balance: Double,

    /** 备注（可空）。批次 D 起**预留**：用户已无手动入口，系统结算也不写它 */
    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    /**
     * 结算水位线：已结算到的最后一天（当天 00:00 时间戳）
     *
     * - `0` = 从来没结算过
     * - 结算时从「水位线 + 1 天」开始补算，所以同一天不会被结算两次
     *
     * ⚠️ `defaultValue = "0"` 必须与 [com.example.personalassistant.core.database.Migration_18_19]
     *    里的 `ADD COLUMN ... NOT NULL DEFAULT 0` 逐字一致 —— 两边不一致 Room 运行期会判成列不一致。
     */
    @ColumnInfo(name = "lastSettledDate", defaultValue = "0")
    val lastSettledDate: Long = 0L,
)
