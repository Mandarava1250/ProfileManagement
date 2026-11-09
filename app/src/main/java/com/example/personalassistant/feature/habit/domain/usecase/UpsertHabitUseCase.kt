package com.example.personalassistant.feature.habit.domain.usecase

import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.repository.HabitRepository
import javax.inject.Inject

/**
 * Phase 23：新增 / 更新习惯。
 *
 * 校验（不合法的输入直接抛错，不静默吞掉）：
 * - 名称不能为空
 * - 目标次数至少 1 次
 */
class UpsertHabitUseCase @Inject constructor(
    private val repository: HabitRepository,
) {
    suspend operator fun invoke(habit: Habit): Long {
        val name = habit.name.trim()
        require(name.isNotEmpty()) { "习惯名称不能为空" }
        require(habit.targetCount >= 1) { "目标次数至少为 1 次" }
        return repository.upsertHabit(habit.copy(name = name))
    }
}
