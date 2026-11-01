package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.ChecklistItem
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 15：观察清单（顶层项 + 标签 + 嵌套子清单）。
 */
class ObserveChecklistItemsUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    operator fun invoke(): Flow<List<ChecklistItem>> = repository.observeChecklistItems()
}
