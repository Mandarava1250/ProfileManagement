package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.NamedFilter
import com.example.personalassistant.feature.task.domain.repository.NamedFilterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 19：命名过滤器仓库的内存替身
 *
 * 语义照搬真实现（同名覆盖、按名删除），只是不落 DataStore。
 */
class FakeNamedFilterRepository : NamedFilterRepository {

    private val state = MutableStateFlow<List<NamedFilter>>(emptyList())

    /** 存过的全部记录（含被覆盖的），方便断言「同名覆盖」而不是「追加」 */
    val saveHistory = mutableListOf<NamedFilter>()

    override fun observe(): Flow<List<NamedFilter>> = state.asStateFlow()

    override suspend fun save(filter: NamedFilter) {
        saveHistory += filter
        state.value = state.value.filterNot { it.name == filter.name } + filter
    }

    override suspend fun delete(name: String) {
        state.value = state.value.filterNot { it.name == name }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}
