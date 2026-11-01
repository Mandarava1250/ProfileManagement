package com.example.personalassistant.feature.task.domain.repository

import com.example.personalassistant.feature.task.domain.model.ChecklistItem
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import kotlinx.coroutines.flow.Flow

/**
 * Phase 15：清单（独立清单项）仓库
 *
 * ## 为什么单独一个接口，而不是往 [TaskRepository] 里塞
 *
 * 清单这一摊（标签挂载 / 嵌套子项 / 分类截止优先级金额）跟任务域的关注点差得挺远，
 * 而且 [TaskRepository] 已经被 8 个测试 Fake 实现了 —— 往里加方法就得同步改 8 个 Fake，
 * 还会跟做 UI 的同伴撞车（androidTest 里的 Fake 也在那个文件里）。
 * 单独开一个接口，两边互不干扰。
 *
 * 清单项本体仍然落在 `sub_tasks` 表（跟任务子清单共用一张表），
 * 只是「清单视角」的读写走这个接口。
 */
interface ChecklistRepository {

    /**
     * 观察清单（顶层项 + 各自的标签 + 各自的嵌套子清单）。
     *
     * 顺序：顶层项按 `order` 升序（同序按创建时间升序）；子清单同理。
     */
    fun observeChecklistItems(): Flow<List<ChecklistItem>>

    /** 按 id 取单个清单项（更新前的部分更新要用它拿当前值）。 */
    suspend fun getItem(id: Long): SubTask?

    /**
     * 新增一个**顶层**独立清单项。
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
    ): Long

    /**
     * 新增一个**嵌套**子清单项（挂在另一个清单项下面）。
     *
     * @param parentSubTaskId 父清单项 id
     */
    suspend fun addNestedItem(parentSubTaskId: Long, title: String): Long

    /** 全字段覆盖式更新（调用方负责带上完整字段）。 */
    suspend fun updateItem(item: SubTask)

    /** 删除清单项（它下面的嵌套子清单由 FK SET_NULL 自动提升为顶层项）。 */
    suspend fun deleteItem(item: SubTask)

    /** 勾选 / 取消勾选。 */
    suspend fun toggleComplete(id: Long, completed: Boolean)

    /** 给清单项挂标签（重复挂载静默忽略）。 */
    suspend fun attachTag(subTaskId: Long, tagId: Long)

    /** 从清单项解绑标签。 */
    suspend fun detachTag(subTaskId: Long, tagId: Long)
}
