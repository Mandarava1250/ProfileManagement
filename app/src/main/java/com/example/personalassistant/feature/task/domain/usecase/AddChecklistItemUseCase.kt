package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.TaskList
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import javax.inject.Inject

/**
 * Phase 15：新增一个顶层清单项（可带分类 / 截止时间 / 优先级 / 金额）。
 *
 * 空白标题会被抛错（与 [AddStandaloneSubTaskUseCase] 行为一致）。
 *
 * Phase 17：新增 [listId] —— 清单项落在哪个清单（默认收集箱）。
 */
class AddChecklistItemUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    suspend operator fun invoke(
        title: String,
        category: String? = null,
        dueDate: Long? = null,
        priority: TaskPriority = TaskPriority.MEDIUM,
        amount: Double? = null,
        listId: Long = TaskList.INBOX_ID,
        /** Phase 22.5：提醒时间（绝对毫秒时间戳，null = 不提醒）。 */
        reminderTime: Long? = null,
    ): Long {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "清单项内容不能为空" }
        return repository.addItem(
            title = trimmed,
            category = category,
            dueDate = dueDate,
            priority = priority,
            amount = amount,
            listId = listId,
            reminderTime = reminderTime,
        )
    }
}
