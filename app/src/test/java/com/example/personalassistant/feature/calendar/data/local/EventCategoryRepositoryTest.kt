package com.example.personalassistant.feature.calendar.data.local

import com.example.personalassistant.feature.calendar.data.repository.FakeEventCategoryRepository
import com.example.personalassistant.feature.calendar.data.seed.DefaultEventCategories
import com.example.personalassistant.feature.calendar.domain.model.EventCategory
import com.example.personalassistant.feature.calendar.domain.repository.EventCategoryRepository
import com.example.personalassistant.feature.calendar.domain.usecase.DeleteEventCategoryUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveEventCategoriesUseCase
import com.example.personalassistant.feature.calendar.domain.usecase.UpsertEventCategoryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 8 Sub-step 9：EventCategory 单元测试
 *
 * - 本项目所有 UseCase 测试使用 Fake 仓库（不依赖 Room）
 * - 覆盖 CRUD / 排序 / 重名校验 / 内置不可删 / 默认 seed
 */
class EventCategoryRepositoryTest {

    private lateinit var fake: FakeEventCategoryRepository
    private lateinit var repository: EventCategoryRepository
    private lateinit var observeUseCase: ObserveEventCategoriesUseCase
    private lateinit var upsertUseCase: UpsertEventCategoryUseCase
    private lateinit var deleteUseCase: DeleteEventCategoryUseCase

    @Before
    fun setup() {
        fake = FakeEventCategoryRepository()
        repository = fake
        observeUseCase = ObserveEventCategoriesUseCase(repository)
        upsertUseCase = UpsertEventCategoryUseCase(repository)
        deleteUseCase = DeleteEventCategoryUseCase(repository)
    }

    // ===== 预置数据 seed =====

    @Test
    fun `seed DefaultEventCategories has 8 entries`() {
        val defaults = DefaultEventCategories.build()
        assertEquals("应有 8 个预置分类", 8, defaults.size)
        assertTrue("包含「工作」", defaults.any { it.name == "工作" && it.isDefault })
        assertTrue("包含「生日」", defaults.any { it.name == "生日" && it.isDefault })
        assertTrue("包含「其他」", defaults.any { it.name == "其他" && it.isDefault })
    }

    @Test
    fun `seed sortOrder is monotonic increasing for first 7`() {
        val defaults = DefaultEventCategories.build()
        for (i in 0 until 6) {
            assertTrue(
                "sortOrder 应该递增：${defaults[i].name}=${defaults[i].sortOrder} < ${defaults[i + 1].name}=${defaults[i + 1].sortOrder}",
                defaults[i].sortOrder < defaults[i + 1].sortOrder,
            )
        }
    }

    // ===== CRUD =====

    @Test
    fun `upsert insert assigns id`() = runTest {
        val id = upsertUseCase(
            EventCategory(name = "运动", color = 0xFF8BC34A.toInt())
        )
        assertTrue("insert 应该返回 > 0 的 id", id > 0L)
        val list = observeUseCase().first()
        assertEquals(1, list.size)
        assertEquals("运动", list[0].name)
        assertEquals(id, list[0].id)
    }

    @Test
    fun `upsert update keeps id and applies new name`() = runTest {
        val id = upsertUseCase(EventCategory(name = "运动", color = 0xFF8BC34A.toInt()))
        upsertUseCase(
            EventCategory(id = id, name = "健身", color = 0xFF689F38.toInt())
        )
        val list = observeUseCase().first()
        assertEquals(1, list.size)
        assertEquals("健身", list[0].name)
        assertEquals(id, list[0].id)
    }

    @Test
    fun `upsert rejects blank name`() = runTest {
        try {
            upsertUseCase(EventCategory(name = "   ", color = 0xFF8BC34A.toInt()))
            fail("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // 预期
        }
    }

    @Test
    fun `upsert rejects duplicate name with different id`() = runTest {
        val firstId = upsertUseCase(EventCategory(name = "运动", color = 0xFF8BC34A.toInt()))
        try {
            upsertUseCase(EventCategory(name = "运动", color = 0xFFE91E63.toInt()))
            fail("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "错误消息应包含'运动'",
                e.message?.contains("运动") == true,
            )
        }
        // first 仍然存在
        assertEquals(firstId, repository.getById(firstId)?.id)
    }

    @Test
    fun `upsert allows same name with same id (self rename)`() = runTest {
        val id = upsertUseCase(EventCategory(name = "运动", color = 0xFF8BC34A.toInt()))
        // 用相同 name 但改了 color 重命名自己
        upsertUseCase(EventCategory(id = id, name = "运动", color = 0xFFE91E63.toInt()))
        val cat = repository.getById(id)
        assertNotNull(cat)
        assertEquals("运动", cat?.name)
        assertEquals(0xFFE91E63.toInt(), cat?.color)
    }

    @Test
    fun `delete removes by id`() = runTest {
        val id = upsertUseCase(EventCategory(name = "运动", color = 0xFF8BC34A.toInt()))
        deleteUseCase(id)
        val list = observeUseCase().first()
        assertEquals(0, list.size)
    }

    @Test
    fun `delete throws on missing id`() = runTest {
        try {
            deleteUseCase(999L)
            fail("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "错误消息应包含'不存在'",
                e.message?.contains("不存在") == true,
            )
        }
    }

    @Test
    fun `delete throws on default category (isDefault=true)`() = runTest {
        val id = upsertUseCase(
            EventCategory(name = "内置", color = 0xFF000000.toInt(), isDefault = true)
        )
        try {
            deleteUseCase(id)
            fail("应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "错误消息应包含'不可删除'",
                e.message?.contains("不可删除") == true,
            )
        }
        // 仍然存在
        assertNotNull(repository.getById(id))
    }

    // ===== 查询 =====

    @Test
    fun `observeAll emits updated list after mutations`() = runTest {
        val flow = observeUseCase()
        // 初始空
        assertEquals(0, flow.first().size)
        // insert
        upsertUseCase(EventCategory(name = "A", color = 0xFF111111.toInt()))
        upsertUseCase(EventCategory(name = "B", color = 0xFF222222.toInt()))
        val list = flow.first()
        assertEquals(2, list.size)
        assertEquals("A", list[0].name)
        assertEquals("B", list[1].name)
    }

    @Test
    fun `findByName returns null on no match`() = runTest {
        upsertUseCase(EventCategory(name = "运动", color = 0xFF8BC34A.toInt()))
        assertNull(repository.findByName("不存在"))
    }

    @Test
    fun `getAll returns full list`() = runTest {
        upsertUseCase(EventCategory(name = "C", color = 0xFF333333.toInt()))
        upsertUseCase(EventCategory(name = "A", color = 0xFF111111.toInt()))
        upsertUseCase(EventCategory(name = "B", color = 0xFF222222.toInt()))
        val all = repository.getAll()
        assertEquals(3, all.size)
        assertEquals(setOf("C", "A", "B"), all.map { it.name }.toSet())
    }
}
