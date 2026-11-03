package com.example.personalassistant.feature.task.domain.model

/**
 * Phase 22.5：新建清单项的默认参数
 *
 * 「默认」指的是**新建那一刻自动填上的值** —— 用户在清单页敲完回车，
 * 不用再去补优先级、提醒时间。
 *
 * 三项各自独立：
 * - [defaultListId]：null = 落到当前所在清单（默认行为）；非 null = 一律落到这个清单
 * - [defaultPriority]：新建时的默认优先级
 * - [defaultReminderMinutesBefore]：截止前多少分钟提醒；null = 不设提醒
 */
data class ChecklistDefaults(
    /**
     * 默认清单 id。
     *
     * null = 跟着当前视图走（在哪个清单就建在哪个清单）—— 这是最自然的行为。
     * 非 null = 一律落到这个清单（比如「所有新东西先丢收集箱」）。
     */
    val defaultListId: Long? = null,

    /** 默认优先级。 */
    val defaultPriority: TaskPriority = TaskPriority.MEDIUM,

    /**
     * 默认提醒：截止前多少分钟提醒。
     *
     * null = 不设提醒；0 = 准点提醒；30 = 提前半小时。
     * 只在**有截止时间**时才有意义 —— 没有截止时间就没法算提醒点。
     */
    val defaultReminderMinutesBefore: Int? = null,
) {
    /** 默认值是否跟「什么都不设」等价 —— 用于 UI 上省掉一次保存。 */
    val isPristine: Boolean
        get() = defaultListId == null &&
            defaultPriority == TaskPriority.MEDIUM &&
            defaultReminderMinutesBefore == null
}
