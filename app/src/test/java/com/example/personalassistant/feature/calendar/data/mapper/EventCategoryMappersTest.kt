package com.example.personalassistant.feature.calendar.data.mapper

import com.example.personalassistant.feature.calendar.data.local.EventCategoryEntity
import com.example.personalassistant.feature.calendar.domain.model.EventCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 Sub-step 9：EventCategoryEntity ↔ EventCategory 双向映射测试
 */
class EventCategoryMappersTest {

    private fun entity(
        id: Long = 1L,
        name: String = "工作",
        color: Int = 0xFF1976D2.toInt(),
        iconKey: String = "Work",
        sortOrder: Int = 10,
        isDefault: Boolean = true,
    ) = EventCategoryEntity(
        id = id,
        name = name,
        color = color,
        iconKey = iconKey,
        sortOrder = sortOrder,
        isDefault = isDefault,
    )

    private fun domain(
        id: Long = 1L,
        name: String = "工作",
        color: Int = 0xFF1976D2.toInt(),
        iconKey: String = "Work",
        sortOrder: Int = 10,
        isDefault: Boolean = true,
    ) = EventCategory(
        id = id,
        name = name,
        color = color,
        iconKey = iconKey,
        sortOrder = sortOrder,
        isDefault = isDefault,
    )

    // ===== toDomain =====

    @Test
    fun `toDomain copies every field`() {
        val src = entity()
        val dst = src.toDomain()

        assertEquals(src.id, dst.id)
        assertEquals(src.name, dst.name)
        assertEquals(src.color, dst.color)
        assertEquals(src.iconKey, dst.iconKey)
        assertEquals(src.sortOrder, dst.sortOrder)
        assertEquals(src.isDefault, dst.isDefault)
    }

    @Test
    fun `toDomain preserves isDefault=false`() {
        val src = entity(isDefault = false)
        val dst = src.toDomain()
        assertEquals(false, dst.isDefault)
    }

    // ===== toEntity =====

    @Test
    fun `toEntity copies every field`() {
        val src = domain()
        val dst = src.toEntity()

        assertEquals(src.id, dst.id)
        assertEquals(src.name, dst.name)
        assertEquals(src.color, dst.color)
        assertEquals(src.iconKey, dst.iconKey)
        assertEquals(src.sortOrder, dst.sortOrder)
        assertEquals(src.isDefault, dst.isDefault)
    }

    // ===== Round-trip =====

    @Test
    fun `round trip toDomain and back preserves all fields`() {
        val src = entity()
        val round = src.toDomain().toEntity()

        assertEquals(src.id, round.id)
        assertEquals(src.name, round.name)
        assertEquals(src.color, round.color)
        assertEquals(src.iconKey, round.iconKey)
        assertEquals(src.sortOrder, round.sortOrder)
        assertEquals(src.isDefault, round.isDefault)
    }

    // ===== 边界：id = 0（新建）=====

    @Test
    fun `domain with id 0 maps to entity with id 0`() {
        val src = domain(id = 0L)
        val dst = src.toEntity()
        assertEquals(0L, dst.id)
    }

    // ===== Domain init 校验：name 不能为空 =====

    @Test
    fun `domain rejects blank name via init block`() {
        try {
            EventCategory(name = "  ", color = 0xFF000000.toInt())
            // Domain 校验：init 块抛 IllegalArgumentException
            // 如果没抛则断言失败
            assertTrue("应抛 IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            // 预期
        }
    }
}
