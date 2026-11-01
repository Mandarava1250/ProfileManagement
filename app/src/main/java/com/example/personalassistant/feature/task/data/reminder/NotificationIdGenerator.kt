package com.example.personalassistant.feature.task.data.reminder

/**
 * NotificationId 工具：把 taskId / eventId 映射成稳定可取消的 Int。
 *
 * 设计：
 * - taskId / eventId 用 Long 防 32 位溢出，需要映射到 Int 给 NotificationManager
 * - 与 CourseReminderScheduler.NOTIFICATION_ID_BASE(100_000) + 课程 ID 段不冲突
 * - 任务段起点 [TASK_NOTIFICATION_ID_BASE] = 200_000
 * - 事件段起点 [EVENT_NOTIFICATION_ID_BASE] = 300_000（Phase 8 Sub-step 1）
 * - 同一 id 重复调度时返回相同 ID，便于覆盖/取消通知
 *
 * 这是个纯函数（只依赖入参），方便单测。
 */
object NotificationIdGenerator {

    /** 任务通知 ID 基址，避免与课程段冲突 */
    const val TASK_NOTIFICATION_ID_BASE = 200_000

    /**
     * 事件通知 ID 基址，避免与课程段（100_000）和任务段（200_000）冲突。
     *
     * Phase 8 Sub-step 1 新增。
     */
    const val EVENT_NOTIFICATION_ID_BASE = 300_000

    /**
     * 把 taskId 映射成 Int 通知 ID。
     *
     * 算法：Int 的合法范围是 [Int.MIN_VALUE, Int.MAX_VALUE]。
     * 基址 200_000，最大可用偏移 = Int.MAX_VALUE - 200_000 ≈ 2.147e9。
     * 取 taskId 的绝对值后 mod (Int.MAX_VALUE - base + 1)，再加 base。
     * 这保证返回值始终在 [base, Int.MAX_VALUE] 区间内，绝不溢出。
     *
     * 设计动机：
     * - 之前用 low31 位直接相加，遇到 taskId 高位恰好让 low31 = Int.MAX_VALUE 时会溢出为负数
     * - Room autoGenerate 总返回正 Long，取绝对值安全
     */
    fun forTask(taskId: Long): Int = baseMappedId(taskId, TASK_NOTIFICATION_ID_BASE)

    /**
     * 把 eventId 映射成 Int 通知 ID（Phase 8 Sub-step 1）。
     *
     * 算法与 [forTask] 一致，基址改为 [EVENT_NOTIFICATION_ID_BASE]，
     * 保证事件段不会与任务段或课程段的 ID 撞码。
     */
    fun forEvent(eventId: Long): Int = baseMappedId(eventId, EVENT_NOTIFICATION_ID_BASE)

    /**
     * 通用映射函数：把 Long id 映射到 [base, Int.MAX_VALUE] 区间内的 Int。
     *
     * 抽出 forTask / forEvent 共用算法，避免重复实现。
     */
    private fun baseMappedId(id: Long, base: Int): Int {
        val abs = if (id == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(id)
        val offsetRange = Int.MAX_VALUE.toLong() - base + 1L
        val offset = (abs % offsetRange).toInt()
        return base + offset
    }
}
