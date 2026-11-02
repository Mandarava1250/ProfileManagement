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
 * - type: 0=支出, 1=收入, 2=转出, 3=转入（2/3 为 Phase 5 跨账户转账）
 * - categoryId: 关联 CategoryEntity.id（同一账户内）
 * - date: 业务时间（用户记账那一刻），毫秒时间戳
 * - createdAt: 入库时间，用于审计
 *
 * ## Phase 5：转账配对
 * 一笔转账写**两条**流水（转出账户一条 type=2、转入账户一条 type=3），
 * 两条共享同一个 [transferPairId]（精确配对），并互填对方的 accountId 到
 * [transferAccountId]（供展示与「删账户时清掉指向它的转账」）。
 * 普通收支流水的 [transferAccountId] 恒为 null。
 *
 * ## 为什么需要两列而不是一列
 * 两个字段服务于**不同**的删除语义，缺一个就会出事故：
 * - [transferPairId]：**删一笔转账**时必须精确删掉两行。若改用
 *   「accountId + 金额 + 日期 + 类型」去认对方，用户连点两下产生的
 *   两笔完全相同的转账会一起命中，删 1 笔静默变成删 2 笔（= 4 行）。
 * - [transferAccountId]：**删账户**时一句
 *   `DELETE ... WHERE accountId = :id OR transferAccountId = :id` 就能把
 *   本账户的转账行、以及其他账户指向本账户的配对行一起清掉。
 *   只存 pairId 的话，这一步得子查询反推配对集，复杂且容易漏。
 * - 展示「转入自 / 转出至 哪个账户」也直接用 [transferAccountId]，不用 JOIN。
 *
 * 转账流水的 [categoryId] 指向本账户内置的「转账」分类（`categories.type = 2`）——
 * 不是可选项：该列是 NOT NULL 且带复合外键，必须挂在真实存在且属于本账户的行上。
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("date"),
        Index("type"),
        // Phase 21：按清单项查流水（清单项详情里列「已记的账」用）
        Index("linkedTaskId"),
        Index("categoryId"),
        Index(value = ["date", "type"]),
        Index(value = ["type", "categoryId", "date"]),
        // Phase 3：记账页/统计查询全部以 accountId 为前导列
        Index(value = ["accountId", "date"]),
        // Phase 5：转账列表按 (账户, 类型) 取，前导 accountId 复用 Phase 3 索引语义
        Index(value = ["accountId", "type"]),
        // 性能（v14）：删一笔转账要按 transferPairId 精确定位那一对流水，
        // 之前没有索引 → 每次删转账都是全表扫（流水越多越慢）。
        Index("transferPairId"),
        // 性能（v14）：删分类前的引用计数按 (accountId, categoryId) 过滤，
        // 现有索引里没有这一对列的组合 → COUNT 时同样退化成全表扫。
        Index(value = ["accountId", "categoryId"])
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

    /** 0=支出, 1=收入, 2=转出, 3=转入 */
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
    val attachmentUri: String? = null,

    /**
     * 转账对方账户 id（Phase 5）；普通收支为 null。
     *
     * ⚠️ **故意不写 `@ColumnInfo(defaultValue = ...)`**：该列可空且迁移里没有
     * DEFAULT 子句，SQLite 的 `dflt_value` 为 NULL；一旦声明 `defaultValue = "NULL"`，
     * Room 会把字符串 "NULL" 写进 schema JSON 与实测值比对，直接判 schema 不匹配，
     * `runMigrationsAndValidate` 就红了。`attachmentUri` / `avatarUri` 同理。
     *
     * 也**不建外键**指向 user_profiles：删账户是「Repository 级联删数据 + 删行」，
     * 不是数据库级联；转账行本来就随所属账户一起被
     * [FinanceDao.deleteTransactionsByAccount] 清掉，加 FK 只会多一条 RESTRICT 死锁面。
     */
    @ColumnInfo(name = "transferAccountId")
    val transferAccountId: Long? = null,

    /**
     * 转账配对 id（Phase 5）：同一笔转账的两行共享同一个 UUID；普通收支为 null。
     *
     * 删除转账靠它保证「一次删且只删这一对」，理由见类注释。
     * 同样**不声明 defaultValue**（可空且无 DEFAULT，对齐 `rrule` 的先例）。
     */
    @ColumnInfo(name = "transferPairId")
    val transferPairId: String? = null,

    /**
     * Phase 21：关联的清单项 id（从清单一键记账时写入）。
     *
     * - null = 手动记的流水（绝大多数）
     * - 非 null = 由某个清单项生成，记账页能顺着它跳回清单项
     *
     * ⚠️ 同样**不声明 defaultValue**（可空且无 DEFAULT，对齐 `transferPairId` 的先例）
     * ⚠️ **故意不加外键**：清单项被删时流水应该留下（钱花出去的事实不该消失），
     * 所以这里是「弱引用」，删任务只会留下一条孤儿指针。
     */
    @ColumnInfo(name = "linkedTaskId")
    val linkedTaskId: Long? = null
)
