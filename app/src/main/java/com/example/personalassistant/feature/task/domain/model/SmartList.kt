package com.example.personalassistant.feature.task.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 17：智能清单（系统视图）
 *
 * 智能清单不是一张表，而是**对任务的筛选视图**：
 * - 收集箱：`listId == INBOX_ID`（快速捕获的默认落点）
 * - 今天 / 明天 / 最近 7 天：按截止日期
 * - 全部：不过滤
 * - 已完成：只看已完成
 *
 * 用户清单（`TaskList`）则是真实的归属地。
 */
enum class SmartList {
    INBOX,
    TODAY,
    TOMORROW,
    NEXT_7_DAYS,
    ALL,
    COMPLETED,
}

/**
 * Phase 17：清单页当前视图
 *
 * 抽屉点一下就切到这里 —— 智能清单走谓词，用户清单走 `listId`。
 */
sealed interface ChecklistView {

    /** 系统智能清单 */
    data class Smart(val smartList: SmartList) : ChecklistView

    /** 用户自建清单 */
    data class User(val listId: Long) : ChecklistView

    /**
     * 四象限视图（Eisenhower 矩阵）—— App 自有特色，非滴答模型。
     *
     * 与 [Smart] / [User] 的区别：它不是「筛选一批任务」，而是**换一种排布**
     * （2×2 矩阵）。所以 [matches] 一律返回 true，实际分组由 UI 层的
     * 矩阵视图负责。
     */
    data object Quadrant : ChecklistView

    companion object {
        /** 默认视图 = 收集箱（滴答的行为：进去先看收集箱） */
        val Default: ChecklistView = Smart(SmartList.INBOX)
    }
}

/**
 * Phase 17：视图谓词 —— 判断一个任务是否属于该视图。
 *
 * 纯函数（便于单测），日期判定用传入的 [today] 与 [zoneId]，
 * 这样「今天」在测试里是确定的，不受运行时刻影响。
 *
 * @param today 「今天」的日期（调用方按本地时区算好传进来）
 */
fun ChecklistView.matches(task: Task, today: LocalDate, zoneId: ZoneId): Boolean = when (this) {
    // 四象限是「换排布」不是「筛一批」—— 全部任务都进，由 UI 层分格
    is ChecklistView.Quadrant -> true

    is ChecklistView.Smart -> when (smartList) {
        SmartList.INBOX -> task.listId == TaskList.INBOX_ID
        SmartList.ALL -> true
        SmartList.COMPLETED -> task.isCompleted
        SmartList.TODAY -> task.isDueOn(today, zoneId)
        SmartList.TOMORROW -> task.isDueOn(today.plusDays(1), zoneId)
        SmartList.NEXT_7_DAYS -> task.isDueWithin(today, 7, zoneId)
    }

    is ChecklistView.User -> task.listId == listId
}

/** 任务是否在指定日期到期（按本地时区换算）。 */
private fun Task.isDueOn(date: LocalDate, zoneId: ZoneId): Boolean {
    val due = dueDate ?: return false
    return Instant.ofEpochMilli(due).atZone(zoneId).toLocalDate() == date
}

/**
 * 任务是否在「今天起 N 天内」到期（含今天，不含第 N+1 天）。
 *
 * 区间是 [today, today+N]：今天、明天、最近 7 天都落在这个窗口里，
 * 语义上跟滴答的「最近 7 天」一致。
 */
private fun Task.isDueWithin(today: LocalDate, days: Long, zoneId: ZoneId): Boolean {
    val due = dueDate ?: return false
    val dueDateOnly = Instant.ofEpochMilli(due).atZone(zoneId).toLocalDate()
    return !dueDateOnly.isBefore(today) && !dueDateOnly.isAfter(today.plusDays(days))
}
