package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Tag
import com.example.personalassistant.feature.task.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 6c：观察标签云（全部标签 + 使用频次，按频次降序）。
 *
 * - 同频次按 createdAt 升序
 * - LEFT JOIN 兜底：0 关联的标签也出现在云里
 *
 * 用于 TasksTab 顶部 TagFilterBar 渲染 chip 行。
 */
class ObserveAllTagsWithCountUseCase @Inject constructor(
    private val repository: TagRepository,
) {
    operator fun invoke(): Flow<List<Tag>> =
        repository.observeAllTagsWithCount()
}