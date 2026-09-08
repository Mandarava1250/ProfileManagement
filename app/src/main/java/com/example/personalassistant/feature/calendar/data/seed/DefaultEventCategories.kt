package com.example.personalassistant.feature.calendar.data.seed

import com.example.personalassistant.feature.calendar.data.local.EventCategoryEntity

/**
 * Phase 8 Sub-step 9：预置日历事件分类（首次安装写入）
 *
 * - 8 个分类覆盖大部分用户场景
 * - isDefault = true，内置不可删
 * - 颜色用 Material 3 调色板（与 Finance categories 风格一致）
 */
object DefaultEventCategories {

    fun build(): List<EventCategoryEntity> = listOf(
        EventCategoryEntity(
            name = "工作",
            color = 0xFF1976D2.toInt(),       // 蓝
            iconKey = "Work",
            sortOrder = 10,
            isDefault = true,
        ),
        EventCategoryEntity(
            name = "学习",
            color = 0xFF388E3C.toInt(),       // 绿
            iconKey = "School",
            sortOrder = 20,
            isDefault = true,
        ),
        EventCategoryEntity(
            name = "生活",
            color = 0xFFE91E63.toInt(),       // 粉
            iconKey = "Home",
            sortOrder = 30,
            isDefault = true,
        ),
        EventCategoryEntity(
            name = "健康",
            color = 0xFFE53935.toInt(),       // 红
            iconKey = "LocalHospital",
            sortOrder = 40,
            isDefault = true,
        ),
        EventCategoryEntity(
            name = "财务",
            color = 0xFF00897B.toInt(),       // 青
            iconKey = "AccountBalanceWallet",
            sortOrder = 50,
            isDefault = true,
        ),
        EventCategoryEntity(
            name = "社交",
            color = 0xFF8E24AA.toInt(),       // 紫
            iconKey = "People",
            sortOrder = 60,
            isDefault = true,
        ),
        EventCategoryEntity(
            name = "生日",
            color = 0xFFFF6F00.toInt(),       // 橙
            iconKey = "Cake",
            sortOrder = 70,
            isDefault = true,
        ),
        EventCategoryEntity(
            name = "其他",
            color = 0xFF6750A4.toInt(),       // 主色（与 Event 默认色一致）
            iconKey = "Label",
            sortOrder = 999,
            isDefault = true,
        ),
    )
}
