package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 6c：观察某任务关联的全部标签。
 *
 * - 排序：createdAt ASC（与 DAO 查询一致）
 * - 用于详情页 TagSection 实时刷新
 */
class ObserveTagsForTaskUseCase @Inject constructor(
    private val repository: TagRepository,
) {
    operator fun invoke(taskId: Long): Flow<List<Tag>> =
        repository.observeTagsForTask(taskId)
}