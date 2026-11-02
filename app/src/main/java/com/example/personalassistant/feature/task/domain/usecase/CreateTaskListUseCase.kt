package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.repository.TaskListRepository
import javax.inject.Inject

/**
 * Phase 17：新建清单
 *
 * 空白名会被抛错（与 [AddStandaloneSubTaskUseCase] 行为一致）。
 *
 * @return 新清单 id（重名时返回已存在那条的 id）
 */
open class CreateTaskListUseCase @Inject constructor(
    private val repository: TaskListRepository,
) {
    open suspend operator fun invoke(
        displayName: String,
        colorHex: Long = TaskListRepository.DEFAULT_COLOR,
        iconKey: String = "",
    ): Long {
        require(displayName.isNotBlank()) { "清单名不能为空" }
        return repository.createList(displayName, colorHex, iconKey)
    }
}
