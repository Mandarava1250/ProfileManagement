package com.example.personalassistant.feature.habit.domain.usecase

import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Phase 23：归档 / 取消归档习惯。
 *
 * 归档 ≠ 删除：历史打卡记录保留，只是不再出现在「今日」列表里。
 */
class ArchiveHabitUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    suspend operator fun invoke(habitId: Long, archived: Boolean) {
        require(habitId > 0L) { "习惯 id 无效" }
        repository.setArchived(habitId, archived)
    }
}
