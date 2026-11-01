package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 子任务实体（任务清单项）
 *
 * 与 TaskEntity 是 1:N 关系，parentTaskId 为 null 时表示独立的顶级清单项
 * （也支持无 Task 的纯清单模式）。任务删除时通过 FK SET_NULL 让子清单自动转为独立项。
 *
 * ## Phase 15（schema v13）：清单项扩展
 *
 * - category：分类名（[com.example.personalassistant.feature.task.domain.model.TaskCategory.PRESETS] 里的名字，如「工作」）
 * - dueDate：截止时间（毫秒时间戳）
 * - priority：优先级（存 [com.example.personalassistant.feature.task.domain.model.TaskPriority] 的 dbValue）
 * - amount：金额（经济程度）
 * - parentSubTaskId：嵌套子清单（自引用 FK → `sub_tasks.id`，onDelete = SET NULL）
 *
 * ⚠️ 顶层清单项 = `parentTaskId IS NULL AND parentSubTaskId IS NULL`；
 * 嵌套子清单项的 parentTaskId 同样是 NULL，靠 parentSubTaskId 区分层级。
 */
@Entity(
    tableName = "sub_tasks",
    indices = [
        Index("parentTaskId"),
        Index(value = ["parentTaskId", "order"]),
        // Phase 15：自引用外键必须有索引，否则 Room 会告警 + 查询退化成全表扫
        Index("parentSubTaskId"),
        // 性能（v14）：清单项按分类筛选（category = :category），
        // 没有索引时每次筛选都要扫全表。
        Index("category"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.SET_NULL
        ),
        // Phase 15：自引用外键 —— 清单项可以再挂子清单项（父项被删时子项自动提升为顶层）
        ForeignKey(
            entity = SubTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentSubTaskId"],
            onDelete = ForeignKey.SET_NULL
        ),
    ]
)
@Serializable
data class SubTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "parentTaskId")
    val parentTaskId: Long?,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "isCompleted")
    val isCompleted: Boolean = false,

    /** 显示顺序 */
    @ColumnInfo(name = "order")
    val order: Int = 0,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    /** 分类名（TaskCategory.PRESETS 里的名字）。null = 未分类 */
    @ColumnInfo(name = "category")
    val category: String? = null,

    /** 截止时间（毫秒时间戳）。null = 无截止 */
    @ColumnInfo(name = "dueDate")
    val dueDate: Long? = null,

    /**
     * 优先级：0=低 1=中 2=高 3=普通 4=紧急（= TaskPriority.dbValue）
     *
     * ⚠️ `defaultValue` 必须与 [com.example.personalassistant.core.database.Migration_12_13]
     * 里的 `DEFAULT 1` 逐字对齐 —— Room 迁移校验会逐列比对 defaultValue，
     * 对不上直接抛 `Migration didn't properly handle: sub_tasks`（线上闪退同款）。
     */
    @ColumnInfo(name = "priority", defaultValue = "1")
    val priority: Int = 1,

    /** 金额（经济程度）。null = 无金额 */
    @ColumnInfo(name = "amount")
    val amount: Double? = null,

    /** 父清单项 id（自引用）。null = 顶层清单项 */
    @ColumnInfo(name = "parentSubTaskId")
    val parentSubTaskId: Long? = null,
)
