package com.example.personalassistant.feature.task.domain.model

/**
 * 任务优先级
 *
 * 数据库存储使用 Int：0=低, 1=中, 2=高
 *
 * - dbValue：DB 列值
 * - label：UI 展示文案
 */
enum class TaskPriority(val dbValue: Int, val label: String) {
    LOW(0, "低"),
    MEDIUM(1, "中"),
    HIGH(2, "高");

    companion object {
        fun fromDb(value: Int): TaskPriority =
            entries.firstOrNull { it.dbValue == value } ?: MEDIUM
    }
}
