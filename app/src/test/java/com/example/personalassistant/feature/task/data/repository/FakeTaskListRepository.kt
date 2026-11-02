package com.example.personalassistant.feature.task.data.repository

import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.model.TaskListWithCount
import com.example.personalassistant.feature.task.domain.repository.TaskListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Phase 17：内存版清单仓库（单测用）
 *
 * 行为对齐 [com.example.personalassistant.feature.task.data.repository.TaskListRepositoryImpl]：
 * - 重名返回已有那条的 id
 * - 内置收集箱（id = 1）不可改名 / 删除 / 归档
 * - 删清单时任务移到收集箱
 */
class FakeTaskListRepository : TaskListRepository {

    private val listsState = MutableStateFlow<List<TaskList>>(emptyList())
    private var nextId = 2L // 1 号留给内置收集箱

    /** 当前存着的全部清单。 */
    val lists: List<TaskList> get() = listsState.value

    /** 直接塞清单（构造测试场景）。 */
    fun setLists(lists: List<TaskList>) {
        listsState.value = lists
        val maxId = lists.maxOfOrNull { it.id } ?: 0L
        if (maxId >= nextId) nextId = maxId + 1
    }

    /** 塞一个内置收集箱（id = 1）。 */
    fun seedInbox() {
        if (listsState.value.none { it.id == TaskList.INBOX_ID }) {
            setLists(
                listOf(
                    TaskList(
                        id = TaskList.INBOX_ID,
                        nameNormalized = "inbox",
                        displayName = "收集箱",
                        isSystem = true,
                    )
                ) + listsState.value
            )
        }
    }

    override fun observeListsWithPendingCount(): Flow<List<TaskListWithCount>> =
        listsState.map { rows -> rows.map { TaskListWithCount(list = it, pendingCount = 0) } }

    override suspend fun getList(id: Long): TaskList? =
        listsState.value.find { it.id == id }

    override suspend fun createList(
        displayName: String,
        colorHex: Long,
        iconKey: String,
    ): Long {
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "清单名不能为空" }
        val normalized = trimmed.lowercase()
        listsState.value.find { it.nameNormalized == normalized }?.let { return it.id }

        val id = nextId++
        listsState.value = listsState.value + TaskList(
            id = id,
            nameNormalized = normalized,
            displayName = trimmed,
            colorHex = colorHex,
            iconKey = iconKey,
            sortOrder = listsState.value.size,
        )
        return id
    }

    override suspend fun renameList(id: Long, displayName: String) {
        require(id != TaskList.INBOX_ID) { "内置收集箱不可改名" }
        val trimmed = displayName.trim()
        require(trimmed.isNotEmpty()) { "清单名不能为空" }
        val normalized = trimmed.lowercase()
        listsState.value.find { it.nameNormalized == normalized }?.let { existing ->
            require(existing.id == id) { "已存在同名清单「${existing.displayName}」" }
        }
        listsState.value = listsState.value.map {
            if (it.id == id) it.copy(nameNormalized = normalized, displayName = trimmed) else it
        }
    }

    override suspend fun updateAppearance(id: Long, colorHex: Long?, iconKey: String?) {
        require(id != TaskList.INBOX_ID) { "内置收集箱不可改外观" }
        listsState.value = listsState.value.map {
            if (it.id == id) {
                it.copy(
                    colorHex = colorHex ?: it.colorHex,
                    iconKey = iconKey ?: it.iconKey,
                )
            } else {
                it
            }
        }
    }

    override suspend fun deleteListAndHandleTasks(id: Long, moveTasksToInbox: Boolean): Int {
        require(id != TaskList.INBOX_ID) { "内置收集箱不可删除" }
        listsState.value = listsState.value.filterNot { it.id == id }
        return 0
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        require(id != TaskList.INBOX_ID) { "内置收集箱不可归档" }
        listsState.value = listsState.value.map {
            if (it.id == id) it.copy(isArchived = archived) else it
        }
    }

    override suspend fun reorderLists(orderedIds: List<Long>) {
        listsState.value = listsState.value.map { list ->
            val index = orderedIds.indexOf(list.id)
            if (index >= 0) list.copy(sortOrder = index) else list
        }
    }

    override suspend fun moveTasksToList(toListId: Long, taskIds: List<Long>): Int = taskIds.size
}
