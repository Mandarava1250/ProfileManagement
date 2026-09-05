package com.example.personalassistant.feature.task.data.reminder

/**
 * NotificationId 工具：把 taskId 映射成稳定可取消的 Int。
 *
 * 设计：
 * - taskId 用 Long 防 32 位溢出，需要映射到 Int 给 NotificationManager
 * - 与 CourseReminderScheduler.NOTIFICATION_ID_BASE(100_000) + 课程 ID 段不冲突
 * - 任务段起点 [TASK_NOTIFICATION_ID_BASE] = 200_000
 * - 同一 taskId 重复调度时返回相同 ID，便于覆盖/取消通知
 *
 * 这是个纯函数（只依赖入参），方便单测。
 */
object NotificationIdGenerator {

    /** 任务通知 ID 基址，避免与课程段冲突 */
    const val TASK_NOTIFICATION_ID_BASE = 200_000

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
    fun forTask(taskId: Long): Int {
        val abs = if (taskId == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(taskId)
        val offsetRange = Int.MAX_VALUE.toLong() - TASK_NOTIFICATION_ID_BASE + 1L
        val offset = (abs % offsetRange).toInt()
        return TASK_NOTIFICATION_ID_BASE + offset
    }
}
