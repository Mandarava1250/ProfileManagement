package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import javax.inject.Inject

/**
 * Phase 6b：在指定任务下新增一个子清单项。
 *
 * - 空白 / 纯空格的标题会被抛错（与 [AddStandaloneSubTaskUseCase] 行为一致）
 * - 调用方（详情页 ViewModel）保证 parentId 对应的 task 已存在
 *
 * Phase 6b：`open` 让 Compose UI / VM 测试 fork 子类覆盖 invoke 直接拦截。
 */
open class AddSubTaskToTaskUseCase @Inject constructor(
    private val repository: TaskRepository,
) {
    open suspend operator fun invoke(parentId: Long, title: String): Long {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "清单项内容不能为空" }
        return repository.addSubTaskToTask(parentId, trimmed)
    }
}
