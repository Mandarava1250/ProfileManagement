package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TagRepository
import javax.inject.Inject

/**
 * Phase 6c：删除标签。
 *
 * - 任务↔标签关联由 FK CASCADE 自动清掉，无需调用方手动 detach
 * - 该 UseCase 当前无返回值（成功即返回 Unit）
 *
 * Phase 6c：`open` 让 Compose UI / VM 测试 fork 子类覆盖 invoke 直接拦截。
 */
open class DeleteTagUseCase @Inject constructor(
    private val repository: TagRepository,
) {
    open suspend operator fun invoke(tagId: Long) {
        repository.deleteTag(tagId)
    }
}