package com.example.personalassistant.feature.task.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 任务实体
 *
 * - priority: 0=低, 1=中, 2=高
 * - repeatType: null=不重复; "daily" | "weekly" | "monthly" | "yearly"
 * - repeatEndDate: 重复截止日期（null=无限）
 * - repeatDays: Phase 4b 自定义星期几存储，格式 "MONDAY,WEDNESDAY"（可选；与 repeatType 配合解读）
 *           为 null 时 repeatType 即为最终语义；非 null 时即使 repeatType 落库为 DAILY 也按自定义星期解读
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index("dueDate"),
        Index("isCompleted"),
        Index("priority"),
        Index(value = ["dueDate", "isCompleted"])
    ]
)
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
)
