package com.example.personalassistant.feature.task.domain.model

/**
 * 任务分类。
 *
 * colorHex 例如 "#FFCDD2"（粉红）。Phase 4a 由代码内置几组预设分类，Phase 5 接入用户自定义。
 */
data class TaskCategory(
    val id: Long,
    val name: String,
    val colorHex: String
) {
    companion object {
        /** 内置预设（Phase 4a 写死） */
        val PRESETS: List<TaskCategory> = listOf(
            TaskCategory(1L, "工作", "#FFCDD2"),
            TaskCategory(2L, "学习", "#C5CAE9"),
            TaskCategory(3L, "生活", "#C8E6C9"),
            TaskCategory(4L, "其他", "#FFE0B2")
        )
    }
}
