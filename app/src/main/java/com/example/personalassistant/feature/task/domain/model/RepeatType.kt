package com.example.personalassistant.feature.task.domain.model

/**
 * 任务重复类型
 *
 * 数据库存储使用 nullable String：null=不重复, "daily"/"weekly"/"monthly"/"yearly"
 */
enum class RepeatType(val dbValue: String?) {
    NONE(null),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly");

    companion object {
        fun fromDb(value: String?): RepeatType =
            entries.firstOrNull { it.dbValue == value } ?: NONE
    }
}
