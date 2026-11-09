package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.ChecklistDefaults
import com.example.personalassistant.feature.task.domain.repository.ChecklistDefaultsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 22.5：清单默认值的内存替身
 *
 * 测试里用：改 `current` 就能验证「默认值套到新建项上」的行为。
 */
class FakeChecklistDefaultsRepository : ChecklistDefaultsRepository {

    /** 当前默认值（测试直接改这个）。 */
    var current: ChecklistDefaults = ChecklistDefaults()

    private val state = MutableStateFlow(current)

    override fun observe(): Flow<ChecklistDefaults> = state.asStateFlow()

    override suspend fun set(defaults: ChecklistDefaults) {
        current = defaults
        state.value = defaults
    }
}
