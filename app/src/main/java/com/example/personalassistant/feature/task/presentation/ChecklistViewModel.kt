package com.example.personalassistant.feature.task.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.usecase.AddStandaloneSubTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteSubTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.ObserveStandaloneChecklistUseCase
import com.example.personalassistant.feature.task.domain.usecase.ToggleSubTaskCompleteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 清单页 ViewModel */
@HiltViewModel
class ChecklistViewModel @Inject constructor(
    observeChecklist: ObserveStandaloneChecklistUseCase,
    private val addSubTask: AddStandaloneSubTaskUseCase,
    private val toggleSubTaskComplete: ToggleSubTaskCompleteUseCase,
    private val deleteSubTask: DeleteSubTaskUseCase,
) : ViewModel() {

    val uiState: StateFlow<ChecklistUiState> = observeChecklist()
        .map { items -> ChecklistUiState(isLoading = false, items = items) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChecklistUiState(),
        )

    fun addItem(title: String) {
        viewModelScope.launch {
            try {
                addSubTask(title)
            } catch (_: IllegalArgumentException) {
                // UseCase 已校验非空，UI 层通常不会传空进来；此处吞掉 IllegalArgumentException
            }
        }
    }

    fun toggleComplete(id: Long, completed: Boolean) {
        viewModelScope.launch { toggleSubTaskComplete(id, completed) }
    }

    fun deleteItem(item: SubTask) {
        viewModelScope.launch { deleteSubTask(item) }
    }
}
