package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TaskListRepository
import javax.inject.Inject

/**
 * Phase 17：重命名清单
 *
 * 空白名会被抛错；内置收集箱不可改名（仓库层会 `require` 挡住）。
 */
open class RenameTaskListUseCase @Inject constructor(
    private val repository: TaskListRepository,
) {
    open suspend operator fun invoke(id: Long, displayName: String) {
        require(displayName.isNotBlank()) { "清单名不能为空" }
        repository.renameList(id, displayName)
    }
}
