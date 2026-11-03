package com.example.personalassistant.feature.task.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.model.ChecklistDefaults
import com.example.personalassistant.feature.task.domain.repository.ChecklistDefaultsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 22.5：新建清单项的默认参数
 *
 * 设置页写它，清单页读它 —— 两边共用同一个 repository 实例（DataStore 单例）。
 */
@HiltViewModel
class ChecklistDefaultsViewModel @Inject constructor(
    private val repository: ChecklistDefaultsRepository,
) : ViewModel() {

    /** 当前默认值。 */
    val defaults: StateFlow<ChecklistDefaults> = repository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChecklistDefaults(),
        )

    /** 保存默认值。 */
    fun save(defaults: ChecklistDefaults) {
        viewModelScope.launch {
            repository.set(defaults)
        }
    }
}
