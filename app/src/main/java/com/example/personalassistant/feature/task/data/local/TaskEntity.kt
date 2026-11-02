package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.personalassistant.core.holiday.model.HolidayStrategy
import kotlinx.serialization.Serializable

/**
 * 任务实体
 *
 * - priority: 0=低, 1=中, 2=高（另有 3=普通, 4=紧急，见 [com.example.personalassistant.feature.task.domain.model.TaskPriority]）
 * - repeatType: null=不重复; "daily" | "weekly" | "monthly" | "yearly"
 * - repeatEndDate: 重复截止日期（null=无限）
 * - repeatDays: Phase 4b 自定义星期几存储，格式 "MONDAY,WEDNESDAY"（可选；与 repeatType 配合解读）
 *           为 null 时 repeatType 即为最终语义；非 null 时即使 repeatType 落库为 DAILY 也按自定义星期解读
 * - holidayStrategy: Phase 8 Sub-step 7，重复任务遇到 HOLIDAY 时的处理策略
 *
 * ## Phase 16（schema v15）：任务 / 清单两套模型合并
 *
 * 这一版把原来的「任务」与「清单项」（`sub_tasks` 表）合并成同一张表：
 * - `listId`：清单归属（FK → `task_lists.id`）。**可空 + DEFAULT 1** ——
 *   SQLite 规定带 `REFERENCES` 的新列 DEFAULT 必须为 NULL（否则无法校验既有行），
 *   所以这里是可空列 + 默认值 1（收集箱）；「归属必须有值」由 Domain / Repository 层保证。
 * - `parentTaskId`：父任务自引用（FK → `tasks.id`）。承载原来的 `sub_tasks.parentSubTaskId` 嵌套语义。
 * - `amount`：金额（原清单项独有，现在任务也能有）
 * - `startAt` / `endAt`：时间段（绝对毫秒时间戳）。两列都有值 = 在日历里是一个时段块。
 * - `completedAt`：完成时间戳（完成率统计要用）
 * - `isPinned`：置顶
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index("dueDate"),
        Index("isCompleted"),
        Index("priority"),
        Index(value = ["dueDate", "isCompleted"]),
        // 性能（v14）：活跃任务列表 = WHERE isCompleted = 0 ORDER BY priority DESC, dueDate ASC，
        // 单列索引各管一段，排序仍要另走临时 B-tree；这个复合索引把「过滤 + 排序」一次做完。
        //
        // ⚠️ `priority` 必须是 **DESC**：实测（artifacts/explain_tasks_index_search.py，5 万行）
        // 升序索引 (isCompleted, priority, dueDate) 仍会出现
        // `USE TEMP B-TREE FOR LAST TERM OF ORDER BY` —— 临时排序没消掉，
        // 排序方向和索引方向相反时 SQLite 用不上索引的有序性。
        // 改成 (isCompleted, priority DESC, dueDate) 后计划里 TEMP B-TREE 整个消失。
        Index(
            value = ["isCompleted", "priority", "dueDate"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC],
        ),
        // Phase 16：按清单筛选（智能清单 / 用户清单的侧边栏视图）
        Index("listId"),
        // Phase 16：自引用外键必须有索引，否则 Room 会告警 + 查询退化成全表扫
        Index("parentTaskId"),
        // Phase 16：某清单下的未完成任务按截止时间排（清单页默认排序）
        Index(value = ["listId", "isCompleted", "dueDate"]),
    ],
    foreignKeys = [
        // 清单归属：删清单 → 连带删它的任务（ON DELETE CASCADE）
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        // 父任务自引用：删父任务 → 子任务提升为顶层（ON DELETE SET NULL）
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.SET_NULL
        ),
    ]
)
@Serializable
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    /** 截止时间（毫秒时间戳，可选）*/
    @ColumnInfo(name = "dueDate")
    val dueDate: Long? = null,

    /** 0=低, 1=中, 2=高 */
    @ColumnInfo(name = "priority")
    val priority: Int = 1,

    @ColumnInfo(name = "isCompleted")
    val isCompleted: Boolean = false,

    /** 用户自定义分类，如「学习」「生活」 */
    @ColumnInfo(name = "category")
    val category: String? = null,

    /** 提醒时间（毫秒时间戳，可选）*/
    @ColumnInfo(name = "reminderTime")
    val reminderTime: Long? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    /** 重复类型：null/Daily/Weekly/Monthly/Yearly */
    @ColumnInfo(name = "repeatType")
    val repeatType: String? = null,

    /** 重复截止日期（可选）*/
    @ColumnInfo(name = "repeatEndDate")
    val repeatEndDate: Long? = null,

    /** Phase 4b：自定义星期几字符串（逗号分隔 DayOfWeek.name），例如 "MONDAY,WEDNESDAY,FRIDAY"。null = 无自定义 */
    @ColumnInfo(name = "repeatDays")
    val repeatDays: String? = null,

    /**
     * Phase 6a：用户拖拽手动排序权重。
     *
     * - null = 未手动排序，沿用 [TaskSort] 行为（优先级 / 截止日期 / ...）
     * - 非 null = 升序展示（值越小越靠前）
     *
     * 仅当用户在 [TaskSort.MANUAL_ORDER] 视图下拖动过顺序才落库。
     * 其他排序方式不影响该字段。
     */
    @ColumnInfo(name = "manualOrder")
    val manualOrder: Long? = null,

    /**
     * Phase 8 Sub-step 7：节假日处理策略（仅对重复任务生效）。
     *
     * 默认 [HolidayStrategy.SKIP]（遇到节假日跳过本次）。
     * 非重复任务（repeatType == null）该字段无效,保留默认值。
     */
    @ColumnInfo(name = "holidayStrategy")
    val holidayStrategy: HolidayStrategy = HolidayStrategy.SKIP,

    // ===== Phase 16（schema v15）：清单体系 =====
    /**
     * 清单归属（FK → `task_lists.id`）。null = 未归属（按收集箱处理）。
     *
     * ⚠️ 刻意声明成可空：SQLite 规定「带 `REFERENCES` 的新列，其 DEFAULT 必须是 NULL」，
     * 所以这一列在 DB 层是可空的 + 默认值 1（收集箱）。
     * `defaultValue = "1"` 必须与 [Migration_14_15] 的 `DEFAULT 1` **逐字一致**，
     * 否则 Room 迁移校验抛 `Migration didn't properly handle: tasks`。
     */
    @ColumnInfo(name = "listId", defaultValue = "1")
    val listId: Long? = 1L,

    /**
     * 父任务 id（自引用 FK → `tasks.id`）。null = 顶层任务。
     *
     * Phase 16 起用它承载原 `sub_tasks.parentSubTaskId` 的嵌套语义：
     * 原本「独立清单项」也是子项的一种（`parentTaskId IS NULL`），合并后统一成
     * 「顶层任务 + 带 parentTaskId 的子任务」两级。
     */
    @ColumnInfo(name = "parentTaskId")
    val parentTaskId: Long? = null,

    /** 金额（经济程度）。null = 无金额。原清单项独有，合并后任务也能有。 */
    @ColumnInfo(name = "amount")
    val amount: Double? = null,

    /** 时间段起点（绝对毫秒时间戳）。与 [endAt] 同时有值 = 在日历里是一个时段块。 */
    @ColumnInfo(name = "startAt")
    val startAt: Long? = null,

    /** 时间段终点（绝对毫秒时间戳）。 */
    @ColumnInfo(name = "endAt")
    val endAt: Long? = null,

    /** 完成时间（绝对毫秒时间戳）。null = 未完成。完成率统计要用。 */
    @ColumnInfo(name = "completedAt")
    val completedAt: Long? = null,

    /** 置顶（恒在列表最前）。 */
    @ColumnInfo(name = "isPinned", defaultValue = "0")
    val isPinned: Boolean = false,
)
