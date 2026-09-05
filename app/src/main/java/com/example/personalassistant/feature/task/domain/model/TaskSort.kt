package com.example.personalassistant.feature.task.domain.model

/**
 * 任务排序方式（首页用）。
 *
 * Phase 4a：内存排序（Repository 层 .sortedWith()），未推到 SQL。
 * Phase 4b：可下沉到 DAO `@RawQuery` 以减少数据量。
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
    TITLE_ASC;

    companion object {
        val DEFAULT: TaskSort = PRIORITY_DUE_ASC
    }
}
