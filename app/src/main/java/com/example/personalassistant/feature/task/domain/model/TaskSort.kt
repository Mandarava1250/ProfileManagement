package com.example.personalassistant.feature.task.domain.model

/**
 * 任务排序方式（首页用）。
 *
 * Phase 4a：内存排序（Repository 层 .sortedWith()），未推到 SQL。
 * Phase 4b：可下沉到 DAO `@RawQuery` 以减少数据量。
 *
 * Phase 6a：新增 [MANUAL_ORDER] —— 用户拖拽后的手动顺序。
 * 落库字段为 [Task.manualOrder]，null 时兜底按 id 升序，避免未拖动过的任务乱跳。
 *
 * 设计动机（扩展位）：
 * - "枚举即策略"模式，新增排序维度仅追加枚举值 + toComparator 分支
 * - 未来加「按标签分组」「按优先级」等同类追加即可
 */
enum class TaskSort {
    /** 优先级高 → 低，再按 dueDate 早 → 晚 */
    PRIORITY_DUE_ASC,

    /** 截止日期早 → 晚 */
    DUE_DATE_ASC,

    /** 截止日期晚 → 早 */
    DUE_DATE_DESC,

    /** 创建时间晚 → 早（默认） */
    CREATED_DESC,

    /** 按标题 A → Z */
    TITLE_ASC,

    /**
     * Phase 6a：用户拖拽手动顺序。
     *
     * - manualOrder 非 null：按 manualOrder 升序
     * - manualOrder 为 null：兜底按 id 升序，保证未拖动过的任务也有稳定顺序
     */
    MANUAL_ORDER;

    companion object {
        val DEFAULT: TaskSort = PRIORITY_DUE_ASC
    }
}

/**
 * Phase 6a：把 toComparator 提到 domain 包顶层（internal）——
 * RepositoryImpl 与同包测试都可以直接复用，**确保测试和生产代码用的是同一份逻辑**。
 */
internal fun TaskSort.toComparator(): Comparator<Task> = when (this) {
    TaskSort.PRIORITY_DUE_ASC -> compareByDescending<Task> { it.priority.rank }
        .thenBy { it.dueDate ?: Long.MAX_VALUE }
    TaskSort.DUE_DATE_ASC -> compareBy { it.dueDate ?: Long.MAX_VALUE }
    TaskSort.DUE_DATE_DESC -> compareByDescending { it.dueDate ?: Long.MIN_VALUE }
    TaskSort.CREATED_DESC -> compareByDescending { it.createdAt }
    TaskSort.TITLE_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    TaskSort.MANUAL_ORDER -> compareBy<Task> { it.manualOrder ?: Long.MAX_VALUE }
        .thenBy { it.id }
}

internal val TaskPriority.rank: Int get() = when (this) {
    TaskPriority.HIGH -> 2
    TaskPriority.MEDIUM -> 1
    TaskPriority.LOW -> 0
}
