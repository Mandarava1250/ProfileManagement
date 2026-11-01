package com.example.personalassistant.feature.task.domain.usecase

import com.example.personalassistant.feature.task.domain.model.RepeatRule
import com.example.personalassistant.feature.task.domain.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 给定一条已完成的任务 + 重复规则，计算下一次出现的日期。
 *
 * 用法（典型场景）：
 * - 用户勾选完成任务时，调用此 UseCase 生成"下一条实例"的 dueDate
 * - 返回 null 表示规则不重复 / 已到 repeatEndDate / 无法生成（CustomWeekdays 为空集等）
 *
 * Phase 4a 行为约定：
 * - 任务未设 dueDate 时返回 null（无锚点日期）
 * - 任务设了 repeatEndDate 且 next > endDate 时返回 null
 * - 任务设了 reminderTime 时，新实例继承 reminderTime（保持同一时刻模式）
 */
class GenerateNextOccurrenceUseCase @Inject constructor() {

    operator fun invoke(
        task: Task,
        rule: RepeatRule,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LocalDate? {
        val currentDate = task.dueDate?.let { dueMillis ->
            Instant.ofEpochMilli(dueMillis).atZone(zone).toLocalDate()
        } ?: return null

        val next = rule.nextOccurrence(currentDate) ?: return null

        // 边界：repeatEndDate 兜底
        task.repeatEndDate?.let { endMillis ->
            val endDate = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
            if (next.isAfter(endDate)) return null
        }

        return next
    }
}
