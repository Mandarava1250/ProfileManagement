package com.example.personalassistant.feature.task.domain.model

/**
 * Phase 17：清单（任务的归属地）
 *
 * 滴答清单（TickTick）的模型是 **清单 → 任务 → 子项** 三层：
 * - 清单是任务的**归属地**（一个任务只属于一个清单）
 * - 标签是跨清单的**属性**
 * - 过滤器是**视图**
 *
 * 这里对应 `task_lists` 表的 domain 投影。
 */
data class TaskList(
    val id: Long,
    /** 小写规范化名（去重键） */
    val nameNormalized: String,
    /** UI 显示名（保留用户输入的大小写） */
    val displayName: String,
    /** ARGB 颜色值 */
    val colorHex: Long,
    /** 图标 key，空串 = 默认图标 */
    val iconKey: String = "",
    /** 抽屉里的排序权重（升序） */
    val sortOrder: Int = 0,
    /** 内置清单（收集箱）不可删除 */
    val isSystem: Boolean = false,
    /** 归档后不在抽屉里显示，任务仍在 */
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
) {
    /** 是否内置收集箱（收集箱不能被改名 / 删除 / 归档） */
    val isInbox: Boolean get() = id == INBOX_ID

    companion object {
        /** 内置收集箱的固定 id（与 `TaskListEntity.INBOX_ID` 一致） */
        const val INBOX_ID = 1L
    }
}

/**
 * Phase 17：清单 + 未完成数的投影（抽屉徽标用）
 */
data class TaskListWithCount(
    val list: TaskList,
    /** 该清单下未完成的任务数 */
    val pendingCount: Int,
)
