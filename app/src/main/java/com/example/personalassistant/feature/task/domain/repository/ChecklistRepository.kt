package com.example.personalassistant.feature.task.domain.repository

import com.example.personalassistant.feature.task.domain.model.ChecklistItem
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import kotlinx.coroutines.flow.Flow

/**
 * Phase 15/16：清单（独立清单项）仓库
 *
 * ## 为什么单独一个接口，而不是往 [TaskRepository] 里塞
 *
 * 清单这一摊（标签挂载 / 嵌套子项 / 分类截止优先级金额）跟任务域的关注点差得挺远，
 * 而且 [TaskRepository] 已经被 8 个测试 Fake 实现了 —— 往里加方法就得同步改 8 个 Fake。
 * 单独开一个接口，两边互不干扰。
 *
 * ## Phase 16 的变化
 *
 * `sub_tasks` 表已并入 `tasks`，所以：
 * - 清单项本体就是 [Task]（不再有独立的 `SubTask` 类型）
 * - 「顶层清单项」= `parentTaskId IS NULL` 的任务
 * - 嵌套子项 = `parentTaskId = 父项 id` 的任务
 *
 * 读取走 [observeChecklistItems]（仓库层组装「一项 + 标签 + 子项」的树），
 * 写入直接落 `tasks` 表。
 */
interface ChecklistRepository {

    /**
     * 观察清单（顶层项 + 各自的标签 + 各自的嵌套子清单）。
     *
     * 顺序：顶层项按 `manualOrder` 升序（同序按创建时间升序）；子清单同理。
     */
    fun observeChecklistItems(): Flow<List<ChecklistItem>>

    /** 按 id 取单个清单项（更新前的部分更新要用它拿当前值）。 */
    suspend fun getItem(id: Long): Task?

    /**
     * 新增一个**顶层**独立清单项（`parentTaskId = null`）。
     *
     * @param category 分类名（null = 未分类）
     * @param dueDate 截止时间（毫秒时间戳，null = 无截止）
     * @param priority 优先级
     * @param amount 金额（null = 无金额）
     * @return 新清单项 id
     */
    suspend fun addItem(
        title: String,
        category: String? = null,
        dueDate: Long? = null,
        priority: TaskPriority = TaskPriority.MEDIUM,
        amount: Double? = null,
        listId: Long = TaskList.INBOX_ID,
        /** Phase 22.5：提醒时间（绝对毫秒时间戳，null = 不提醒）。 */
        reminderTime: Long? = null,
    ): Long

    /**
     * 新增一个**嵌套**子清单项（挂在另一个清单项下面）。
     *
     * @param parentSubTaskId 父清单项 id
     */
    suspend fun addNestedItem(parentSubTaskId: Long, title: String): Long

    /** 全字段覆盖式更新（调用方负责带上完整字段）。 */
    suspend fun updateItem(item: Task)

    /** 删除清单项（它下面的嵌套子清单由 FK SET_NULL 自动提升为顶层项）。 */
    suspend fun deleteItem(item: Task)

    /** 勾选 / 取消勾选。 */
    suspend fun toggleComplete(id: Long, completed: Boolean)

    /** 给清单项挂标签（重复挂载静默忽略）。 */
    suspend fun attachTag(subTaskId: Long, tagId: Long)

    /** 从清单项解绑标签。 */
    suspend fun detachTag(subTaskId: Long, tagId: Long)
}
