package com.example.personalassistant.feature.timetable.domain.model

/**
 * 学期领域模型
 *
 * - name 作为业务主键（如 "2024-2025-1"）
 * - isCurrent 在应用层保证全局唯一为 true
 */
data class Semester(
    val name: String,
    val startDate: Long,
    val endDate: Long,
    val isCurrent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    init {
        require(name.isNotBlank()) { "学期名称不能为空" }
        require(endDate >= startDate) { "学期结束日期不能早于开始日期" }
    }
}