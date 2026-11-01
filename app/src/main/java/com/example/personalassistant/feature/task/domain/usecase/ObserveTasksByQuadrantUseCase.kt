package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskGroups
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 观察任务按 P1-P4 + 未排期 分组的结果。
 *
 * 把两个 Flow（active / completed）合并，并在内存里做分类。
 * 该分类是纯函数，便于单测覆盖。
 */
class ObserveTasksByQuadrantUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    operator fun invoke(): Flow<TaskGroups> =
        combine(
            repository.observeActive(),
            repository.observeCompleted(),
        ) { active, completed ->
            classify(active, completed)
        }

    companion object {
        /**
         * 当前系统时区的"今天结束时刻"（23:59:59.999）。
         *
         * 用作"今天 vs 未来"的判定边界。dueDate 落在这一天之内的，归入紧急象限。
         */
        fun todayEndMillis(now: Long = System.currentTimeMillis()): Long {
            val zone = ZoneId.systemDefault()
            val date = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return date.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
        }

        /** 单条任务的象限判定。 */
        fun classifyOne(task: Task, todayEndMillis: Long): TaskQuadrant {
            val due = task.dueDate ?: return TaskQuadrant.UNSCHEDULED
            val isUrgent = due <= todayEndMillis
            return when (task.priority) {
                // Phase 8 Sub-step 2：URGENT 与 HIGH 同象限（紧急 = 重要）
                TaskPriority.URGENT, TaskPriority.HIGH ->
                    if (isUrgent) TaskQuadrant.P1 else TaskQuadrant.P2
                TaskPriority.MEDIUM ->
                    if (isUrgent) TaskQuadrant.P2 else TaskQuadrant.P3
                // Phase 8 Sub-step 2：NORMAL 与 LOW 同象限（普通 = 不重要）
                TaskPriority.NORMAL, TaskPriority.LOW ->
                    if (isUrgent) TaskQuadrant.P3 else TaskQuadrant.P4
            }
        }

        /** 把一组任务分类成 TaskGroups。已完成单独放底部折叠区，不分象限。 */
        fun classify(active: List<Task>, completed: List<Task>, todayEndMillis: Long = todayEndMillis()): TaskGroups {
            val byQuadrant = linkedMapOf<TaskQuadrant, MutableList<Task>>()
            active.forEach { task ->
                val q = classifyOne(task, todayEndMillis)
                byQuadrant.getOrPut(q) { mutableListOf() }.add(task)
            }
            // 用 toMap 冻结成不可变 Map 给上游
            return TaskGroups(
                byQuadrant = byQuadrant.mapValues { it.value.toList() },
                completed = completed,
            )
        }
    }
}
