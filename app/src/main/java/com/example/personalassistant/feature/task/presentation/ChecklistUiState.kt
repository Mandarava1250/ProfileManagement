package com.example.personalassistant.feature.task.presentation

import com.example.personalassistant.feature.task.domain.model.SubTask

/** 清单页 UI 状态 */
data class ChecklistUiState(
    val isLoading: Boolean = true,
    val items: List<SubTask> = emptyList(),
)
