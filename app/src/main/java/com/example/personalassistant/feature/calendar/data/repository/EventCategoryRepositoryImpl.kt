package com.example.personalassistant.feature.calendar.data.repository

import com.example.personalassistant.feature.calendar.data.local.EventCategoryDao
import com.example.personalassistant.feature.calendar.data.mapper.toDomain
import com.example.personalassistant.feature.calendar.data.mapper.toEntity
import com.example.personalassistant.feature.calendar.domain.model.EventCategory
import com.example.personalassistant.feature.calendar.domain.repository.EventCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 8 Sub-step 9：EventCategoryRepository 的 Room 实现
 *
 * @Inject + @Singleton：Hilt 注入
 * 由 [com.example.personalassistant.feature.calendar.di.EventCategoryModule] 的 @Binds 绑定
 */
@Singleton
class EventCategoryRepositoryImpl @Inject constructor(
    private val dao: EventCategoryDao,
) : EventCategoryRepository {

    override fun observeAll(): Flow<List<EventCategory>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): EventCategory? =
        dao.getById(id)?.toDomain()

    override suspend fun getAll(): List<EventCategory> =
        dao.getAll().map { it.toDomain() }

    override suspend fun insert(category: EventCategory): Long =
        dao.insert(category.toEntity())

    override suspend fun update(category: EventCategory) {
        require(category.id > 0L) { "update 需要 id > 0, got ${category.id}" }
        dao.update(category.toEntity())
    }

    override suspend fun delete(id: Long) {
        require(id > 0L) { "delete 需要 id > 0, got $id" }
        // 删除前把该分类下的所有 event 的 categoryId 设为 null（避免脏数据）
        dao.detachEvents(id)
        dao.deleteById(id)
    }

    override suspend fun findByName(name: String): EventCategory? =
        dao.findByName(name)?.toDomain()
}
