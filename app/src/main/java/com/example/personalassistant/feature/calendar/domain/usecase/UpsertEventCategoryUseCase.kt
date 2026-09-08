package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.model.EventCategory
import com.example.personalassistant.feature.calendar.domain.repository.EventCategoryRepository
import javax.inject.Inject

/**
 * Phase 8 Sub-step 9：upsert 分类
 *
 * - id == 0 → insert，返回落库 id
 * - id > 0 → update
 * - 名称唯一性校验：与现有任一分类重名（且非自身）时拒绝
 */
class UpsertEventCategoryUseCase @Inject constructor(
    private val repository: EventCategoryRepository,
) {
    suspend operator fun invoke(category: EventCategory): Long {
        val trimmed = category.copy(name = category.name.trim())
        require(trimmed.name.isNotBlank()) { "分类名不能为空" }

        // 重名校验：查找同名分类，若存在且不是自身 id → 拒绝
        val existing = repository.findByName(trimmed.name)
        if (existing != null && existing.id != trimmed.id) {
            throw IllegalArgumentException("已存在同名分类：${trimmed.name}")
        }

        return if (trimmed.id == 0L) {
            repository.insert(trimmed)
        } else {
            repository.update(trimmed)
            trimmed.id
        }
    }
}
