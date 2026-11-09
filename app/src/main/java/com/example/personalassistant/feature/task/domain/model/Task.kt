package com.example.personalassistant.feature.task.domain.model

import com.example.personalassistant.core.holiday.model.HolidayStrategy

/**
 * 任务（Domain 模型）
 *
 * 字段含义对齐 TaskEntity，但把 priority 和 repeatType 提升为强类型枚举，
 * 业务层不再与 Int/String 直接打交道。
 */
data class Task(
    val id: Long = 0L,
    val title: String,
    val description: String? = null,
    val dueDate: Long? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val isCompleted: Boolean = false,
    val category: String? = null,
    val reminderTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatEndDate: Long? = null,
    /**
     * Phase 4b：自定义星期几（与 RepeatRule.CustomWeekdays 配套）。
     * 字符串格式 "MONDAY,WEDNESDAY,FRIDAY"，null = 无自定义。
     * - 当业务层持有 RepeatRule.CustomWeekdays(days) 时，转 Entity 之前会先把 days 序列化到这里
     * - 当业务层读出 RepeatRule 时，会从 repeatType + repeatDays 还原为 RepeatRule 对象
     */
    val repeatDays: String? = null,

    /**
     * Phase 6a：用户拖拽手动排序权重。
     *
     * - null = 未手动排序，沿用 [TaskSort] 行为
     * - 非 null = 升序展示（值越小越靠前）
     *
     * 与 [TaskEntity.manualOrder] 一一对应；只由 [TaskSort.MANUAL_ORDER] 路径触发落库。
     */
    val manualOrder: Long? = null,

    /**
     * Phase 8 Sub-step 7+8：节假日处理策略（仅对重复任务生效）。
     *
     * - 默认 [HolidayStrategy.SKIP]（遇到节假日跳过本次）
     * - 非重复任务（repeatType == NONE）该字段无效，保留默认值
     * - 用户在 TaskEditScreen 选择后通过 [Task.holidayStrategy] 写回 Entity
     */
    val holidayStrategy: HolidayStrategy = HolidayStrategy.SKIP,

    // ===== Phase 16：清单体系（吸收原「清单项」的字段） =====

    /**
     * 清单归属 id。指向 `task_lists` 表。
     *
     * DB 层这一列可空（SQLite 规定带 `REFERENCES` 的新列 DEFAULT 必须为 NULL），
     * 但**业务层保证它一定有值** —— 拿不到就按内置收集箱（`TaskListEntity.INBOX_ID`）处理。
     */
    val listId: Long = 1L,

    /**
     * 父任务 id。null = 顶层任务。
     *
     * Phase 16 起用它统一表达层级：原来「任务 → 子清单 → 嵌套子清单」三层
     * 现在统一成「任务 → 子任务」的 `parentTaskId` 链。
     */
    val parentTaskId: Long? = null,

    /** 金额（经济程度）。null = 无金额。 */
    val amount: Double? = null,

    /** 时间段起点（绝对毫秒时间戳）。与 [endAt] 同时有值 = 在日历里是一个时段块。 */
    val startAt: Long? = null,

    /** 时间段终点（绝对毫秒时间戳）。 */
    val endAt: Long? = null,

    /** 完成时间（绝对毫秒时间戳）。null = 未完成。 */
    val completedAt: Long? = null,

    /** 置顶（恒在列表最前）。 */
    val isPinned: Boolean = false,

    // ===== Phase 22.2：搜索命中来源（纯展示用，不落库） =====

    /**
     * 所属清单名。
     *
     * 只有搜索结果会填 —— 搜「Work」时，清单项标题里可能没有 Work，
     * 它是在 Work 清单里才命中的，不说清楚用户会懵。
     * null = 未知或未填。
     */
    val listName: String? = null,

    /**
     * 标签名（搜索命中来源）。
     *
     * 同 [listName] —— 只在搜索结果里填，普通查询路径留空。
     */
    val tagNames: List<String> = emptyList(),
)
