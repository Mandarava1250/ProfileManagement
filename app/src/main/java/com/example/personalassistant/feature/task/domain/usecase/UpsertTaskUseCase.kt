package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * 保存任务及其子清单。task.id == 0L 表示新增，返回新 rowId。
 *
 * 实现层负责把 task.updatedAt 刷新为当前时间。
 */
class UpsertTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    suspend operator fun invoke(task: Task, subTasks: List<SubTask>): Long {
        require(task.title.isNotBlank()) { "任务标题不能为空" }
        val withTimestamp = if (task.id == 0L) task else task.copy(updatedAt = System.currentTimeMillis())
        return repository.upsertTask(withTimestamp, subTasks)
    }
}
