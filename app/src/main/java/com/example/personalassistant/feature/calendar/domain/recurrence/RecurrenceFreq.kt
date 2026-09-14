package com.example.personalassistant.feature.calendar.domain.recurrence

/**
 * Phase 8 Sub-step 9 Step 3：重复频率（RFC 5545 §3.3.10 FREQ 关键字的语义子集）
 *
 * 涟宝选最常见的 4 个：
 * - DAILY：按天
 * - WEEKLY：按周
 * - MONTHLY：按月
 * - YEARLY：按年
 *
 * SECONDLY/MINUTELY/HOURLY 这种细粒度不支持（事件最小单位是天）。
 */
enum class RecurrenceFreq(val rfcName: String) {
    DAILY("DAILY"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY"),
}