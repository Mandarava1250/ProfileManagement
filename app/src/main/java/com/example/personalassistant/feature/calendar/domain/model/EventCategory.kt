package com.example.personalassistant.feature.calendar.domain.model

/**
 * Phase 8 Sub-step 9：日历事件分类（Domain 模型）
 *
 * - [id] = 0L 表示新建未保存
 * - [name] 用户可读名称
 * - [color] ARGB，用于在 DayDetailSheet / EventCard 显示色块
 * - [iconKey] Material Icon 名称（保留扩展性，先用字符串）
 * - [sortOrder] 数字越小越靠前
 * - [isDefault] 系统内置不可删除（仅可改名 / 改色）
 */
data class EventCategory(
    val id: Long = 0L,
    val name: String,
    val color: Int,
    val iconKey: String = "Label",
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "分类名不能为空" }
    }

    companion object {
        /** 预置分类的默认色（与 CalendarScreen 主色一致） */
        const val DEFAULT_COLOR: Int = 0xFF6750A4.toInt()
    }
}
