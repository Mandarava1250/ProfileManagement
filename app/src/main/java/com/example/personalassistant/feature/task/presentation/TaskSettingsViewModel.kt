package com.example.personalassistant.feature.task.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.model.TaskDisplayPreferences
import com.example.personalassistant.feature.task.domain.model.TaskQuadrant
import com.example.personalassistant.feature.task.domain.repository.TaskDisplayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 13：任务显示偏好设置 ViewModel
 *
 * 任务这一侧真正算「长期设置」的只有**象限别名**（把"立即执行"改成"马上做"这类）。
 * 视图模式（列表 / 矩阵）属于"当前正在看哪个"，是会话便利性记忆，留在任务页顶部的即时切换器。
 *
 * 与任务页的长按改名共用同一个 [TaskDisplayRepository]，所以两边改的是同一份持久化数据。
 */
@HiltViewModel
class TaskSettingsViewModel @Inject constructor(
    private val displayRepository: TaskDisplayRepository,
) : ViewModel() {

    /**
     * 当前显示偏好（别名表 + 视图模式）
     *
     * 用 [SharingStarted.Eagerly]：设置页一打开就要显示别名，
     * 而别名又被别的流（长按改名）改动 —— 懒启动的话会读到旧值。
     */
    val preferences: StateFlow<TaskDisplayPreferences> = displayRepository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TaskDisplayPreferences(),
        )

    /**
     * 改写某象限的展示别名
     *
     * 空串 / 纯空白直接忽略 —— 别名空着的话界面会退化成空白 Tab，比显示原名更糟。
     */
    fun setAlias(quadrant: TaskQuadrant, alias: String) {
        val trimmed = alias.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            displayRepository.setAlias(quadrant, trimmed)
        }
    }
}
