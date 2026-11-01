package com.example.personalassistant.feature.calendar.data.repository

import com.example.personalassistant.feature.calendar.domain.model.EventCategory
import com.example.personalassistant.feature.calendar.domain.repository.EventCategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Phase 8 Sub-step 9：单元测试用的内存版 [EventCategoryRepository]
 *
 * - 用 MutableStateFlow 模拟 Room 的响应式行为
 * - **不**实现 detach 行为（Fake 直接清空数据；测试 RepositoryImpl detach 时直接看事件 categoryId）
 * - id 自增（nextId 从 1 开始）
 */
class FakeEventCategoryRepository : EventCategoryRepository {

    private val state = MutableStateFlow<List<EventCategory>>(emptyList())
    private var nextId = 1L

    var addCount: Int = 0
        private set
    var updateCount: Int = 0
        private set
    var deleteCount: Int = 0
        private set

    override fun observeAll(): Flow<List<EventCategory>> = state

    override suspend fun getById(id: Long): EventCategory? =
        state.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<EventCategory> = state.value

    override suspend fun insert(category: EventCategory): Long {
        val id = nextId++
        addCount++
        state.value = state.value + category.copy(id = id)
        return id
    }

    override suspend fun update(category: EventCategory) {
        if (category.id <= 0L) return
        updateCount++
        state.value = state.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(id: Long) {
        if (id <= 0L) return
        deleteCount++
        state.value = state.value.filter { it.id != id }
    }

    override suspend fun findByName(name: String): EventCategory? =
        state.value.firstOrNull { it.name == name }

    // ===== 测试辅助 helpers =====

    fun clear() {
        state.value = emptyList()
    }
}
