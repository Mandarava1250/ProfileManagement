package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TagRepository
import javax.inject.Inject

/**
 * Phase 6c：从任务上卸下标签。
 *
 * - 关联不存在时静默跳过（无副作用）
 *
 * Phase 6c：`open` 让 Compose UI / VM 测试 fork 子类覆盖 invoke 直接拦截。
 */
open class RemoveTagFromTaskUseCase @Inject constructor(
    private val repository: TagRepository,
) {
    open suspend operator fun invoke(taskId: Long, tagId: Long) {
        repository.removeTagFromTask(taskId, tagId)
    }
}