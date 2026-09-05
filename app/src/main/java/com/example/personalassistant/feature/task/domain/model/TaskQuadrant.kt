package com.example.personalassistant.feature.task.domain.model

/**
 * 艾森豪威尔四象限 + 未排期
 *
 * 分类规则：
 * - P1 立即执行：priority=HIGH 且 dueDate <= 今天
 * - P2 计划安排：priority=HIGH 且 dueDate > 今天
 *                 或 priority=MEDIUM 且 dueDate <= 今天
 * - P3 委托/简化：priority=MEDIUM 且 dueDate > 今天
 *                 或 priority=LOW 且 dueDate <= 今天
 * - P4 消除：      priority=LOW 且 dueDate > 今天
 * - UNSCHEDULED 未排期：dueDate 为 null
 */
enum class TaskQuadrant(val label: String) {
    P1("立即执行"),
    P2("计划安排"),
    P3("委托/简化"),
    P4("消除"),
    UNSCHEDULED("未排期"),
}

/**
 * 主页数据分组结果
 *
 * - byQuadrant：未完成任务按象限分组（不保证每个象限都有 key）
 * - completed：已完成任务，单独放底部折叠区
 */
data class TaskGroups(
    val byQuadrant: Map<TaskQuadrant, List<Task>>,
    val completed: List<Task>,
) {
    val isEmpty: Boolean
        get() = byQuadrant.values.all { it.isEmpty() } && completed.isEmpty()

    fun countByQuadrant(quadrant: TaskQuadrant): Int =
        byQuadrant[quadrant]?.size ?: 0
}
