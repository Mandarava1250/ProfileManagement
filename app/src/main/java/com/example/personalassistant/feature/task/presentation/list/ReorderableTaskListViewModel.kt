package com.example.personalassistant.feature.task.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.task.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 6a：拖拽排序 VM（单象限内）。
 *
 * 设计动机：
 * - 拖拽过程中只更新本地状态（[localOrder]），避免每个像素位置触发 DB 写入
 * - 仅在 [onDragEnd] 时一次性 [TaskRepository.reorderTasks] 提交
 *
 * 调用方（ReorderableTaskList）负责：
 * - 提供当前象限的 [snapshot]（已排好序的任务）
 * - 在拖拽开始时调用 [beginDrag] / 过程中调用 [moveLocal] / 结束时调用 [endDrag]
 *
 * 跨象限禁止：每个象限各自持自己的 VM 实例 / scope（[scopeKey] 区分）。
 */
@HiltViewModel
class ReorderableTaskListViewModel @Inject constructor(
    private val repository: TaskRepository,
) : ViewModel() {

    /** 当前可见任务（拖拽过程中覆盖原 snapshot） */
    private val _localOrder = MutableStateFlow<List<Long>>(emptyList())
    val localOrder: StateFlow<List<Long>> = _localOrder.asStateFlow()

    /** 拖拽中标志（UI 用来加阴影/高亮） */
    private val _isDragging = MutableStateFlow(false)
    val isDragging: StateFlow<Boolean> = _isDragging.asStateFlow()

    /** 跨象限区分：保证两个象限同时拖拽互不干扰（实际不会触发，但显式标注语义） */
    var scopeKey: String = ""

    /**
     * 拖拽开始：记录当前快照作为本地顺序（保留原顺序）。
     */
    fun beginDrag(snapshot: List<Long>) {
        if (_isDragging.value) return
        _localOrder.value = snapshot
        _isDragging.value = true
    }

    /**
     * 拖拽中：单条移动。不触发 DB 写，纯内存。
     */
    fun moveLocal(fromIndex: Int, toIndex: Int) {
        if (!_isDragging.value) return
        val current = _localOrder.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _localOrder.value = current
    }

    /**
     * 拖拽结束：一次性提交顺序到 DB。
     *
     * - 若本地顺序与原顺序一致（snapshot），跳过 DB 写入（幂等优化）
     * - 否则 [TaskRepository.reorderTasks] 在事务内批量更新
     */
    fun endDrag(originalSnapshot: List<Long>) {
        if (!_isDragging.value) return
        _isDragging.value = false
        val finalOrder = _localOrder.value
        if (finalOrder == originalSnapshot) return
        viewModelScope.launch {
            repository.reorderTasks(finalOrder)
        }
    }

    /**
     * 数据来源变化（DB emit 新值）：重置本地顺序。
     * 调用方应在 collect snapshot 时调用此方法。
     */
    fun resetLocalOrder(newSnapshot: List<Long>) {
        if (_isDragging.value) return  // 拖拽中不要重置
        _localOrder.value = newSnapshot
    }
}
