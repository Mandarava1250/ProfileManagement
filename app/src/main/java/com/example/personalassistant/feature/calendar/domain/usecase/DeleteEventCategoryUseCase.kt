package com.example.personalassistant.feature.calendar.domain.usecase

import com.example.personalassistant.feature.calendar.domain.repository.EventCategoryRepository
import javax.inject.Inject

/**
 * Phase 8 Sub-step 9：删除分类
 *
 * - 系统内置（isDefault = true）不可删
 * - 删除前会先 detach 该分类下的所有 event（Repository 内部做）
 */
class DeleteEventCategoryUseCase @Inject constructor(
    private val repository: EventCategoryRepository,
) {
    suspend operator fun invoke(id: Long) {
        require(id > 0L) { "id 必须 > 0" }
        val category = repository.getById(id)
            ?: throw IllegalArgumentException("分类不存在：id=$id")
        if (category.isDefault) {
            throw IllegalArgumentException("系统内置分类不可删除：${category.name}")
        }
        repository.delete(id)
    }
}
