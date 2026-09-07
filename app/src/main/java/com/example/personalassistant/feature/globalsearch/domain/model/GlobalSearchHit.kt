package com.example.personalassistant.feature.globalsearch.domain.model

/**
 * Phase 8 Sub-step 3 全局搜索 — 命中项类型
 *
 * - [Task] / [Event] / [Transaction] / [Course] 对应 4 个数据源
 * - 每条命中带 [entityId] 用于跳转
 */
enum class GlobalSearchHitType {
    TASK,
    EVENT,
    TRANSACTION,
    COURSE,
}

/**
 * 全局搜索聚合后的单条命中项（不可变快照，UI 直接渲染）
 *
 * @param type 命中类型，决定走哪个详情页
 * @param entityId 对应数据源的 id（与 [type] 一致即可跳转）
 * @param title 主标题（如任务标题 / 事件标题 / 流水备注 / 课程名）
 * @param subtitle 副标题（如日期 / 分类名 / 老师 / 金额），可空
 * @param highlight 命中关键字所在的片段（用于 UI 高亮），可空
 * @param timestamp 排序权重（毫秒时间戳，Task=dueDate, Event=date, Transaction=date, Course=0）
 */
data class GlobalSearchHit(
    val type: GlobalSearchHitType,
    val entityId: Long,
    val title: String,
    val subtitle: String? = null,
    val highlight: String? = null,
    val timestamp: Long = 0L,
)

/**
 * 全局搜索聚合结果
 *
 * - [query] 当前关键字
 * - [tasks] 任务命中（按 title / description / category）
 * - [events] 事件命中（按 title / note / category）
 * - [transactions] 流水命中（按 note / category name）
 * - [courses] 课程命中（按 name / teacher / location / note）
 *
 * 排序：各列表内已由 DAO ORDER BY 决定，调用方再做扁平化时按 [GlobalSearchHit.timestamp] 倒序
 */
data class GlobalSearchResult(
    val query: String,
    val tasks: List<GlobalSearchHit> = emptyList(),
    val events: List<GlobalSearchHit> = emptyList(),
    val transactions: List<GlobalSearchHit> = emptyList(),
    val courses: List<GlobalSearchHit> = emptyList(),
) {
    val totalCount: Int
        get() = tasks.size + events.size + transactions.size + courses.size

    val isEmpty: Boolean
        get() = totalCount == 0

    /** 按 timestamp 倒序扁平化，用于统一列表展示 */
    fun flattened(): List<GlobalSearchHit> =
        (tasks + events + transactions + courses).sortedByDescending { it.timestamp }
}
