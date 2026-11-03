package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.repository.ChecklistRepository
import javax.inject.Inject

/**
 * Phase 18.7：撤销删除 —— 把清单项放回去
 *
 * 按**原 id** 恢复：恢复后 id 不变，指向它的标签关联、流水弱引用都还对得上。
 */
class RestoreChecklistItemUseCase @Inject constructor(
    private val repository: ChecklistRepository,
) {
    suspend operator fun invoke(item: Task): Long = repository.restoreItem(item)
}
