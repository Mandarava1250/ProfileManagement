package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.EventCategory
import com.example.personalassistant.feature.calendar.domain.repository.EventCategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Phase 8 Sub-step 9：观察全部分类（Flow）
 */
class ObserveEventCategoriesUseCase @Inject constructor(
    private val repository: EventCategoryRepository,
) {
    operator fun invoke(): Flow<List<EventCategory>> = repository.observeAll()
}
