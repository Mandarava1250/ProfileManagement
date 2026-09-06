package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TagRepository
import javax.inject.Inject

/**
 * Phase 6c：把标签绑到任务上。
 *
 * - taskId 对应的任务不存在时由 FK 报错（应不会发生，详情页 init 时已加载）
 * - 已存在关联时 OnConflictStrategy.IGNORE 静默跳过
 *
 * Phase 6c：`open` 让 Compose UI / VM 测试 fork 子类覆盖 invoke 直接拦截。
 */
open class AddTagToTaskUseCase @Inject constructor(
    private val repository: TagRepository,
) {
    open suspend operator fun invoke(taskId: Long, tagId: Long) {
        repository.addTagToTask(taskId, tagId)
    }
}