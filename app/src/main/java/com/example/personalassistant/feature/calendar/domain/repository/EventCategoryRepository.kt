package com.example.personalassistant.feature.calendar.domain.repository

import com.example.personalassistant.feature.calendar.domain.model.EventCategory
import kotlinx.coroutines.flow.Flow

/**
 * Phase 8 Sub-step 9：Event 分类仓库接口
 *
 * - 读：observeAll（Flow）+ getById + getAll
 * - 写：upsert / delete
 *
 * 设计取舍：
 * - upsert(id == 0 → insert / > 0 → update) 在 UseCase 层判断；Repository 只暴露 insert/update
 * - 删除分类时**不**自动清理 events.categoryId（UseCase 层决定策略）
 */
interface EventCategoryRepository {
    fun observeAll(): Flow<List<EventCategory>>
    suspend fun getById(id: Long): EventCategory?
    suspend fun getAll(): List<EventCategory>
    suspend fun insert(category: EventCategory): Long
    suspend fun update(category: EventCategory)
    suspend fun delete(id: Long)
    suspend fun findByName(name: String): EventCategory?
}
