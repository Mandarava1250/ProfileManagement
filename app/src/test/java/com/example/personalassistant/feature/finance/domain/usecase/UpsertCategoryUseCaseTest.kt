package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Phase 7a：UpsertCategoryUseCase 单元测试
 *
 * 覆盖：
 * - name 非空校验（IllegalArgumentException）
 * - name 自动 trim
 * - id=0 时新增（id > 0），id>0 时更新（id 不变）
 * - observe 流能立即看到变化
 */
class UpsertCategoryUseCaseTest {

    private lateinit var repo: FakeFinanceRepository
    private lateinit var useCase: UpsertCategoryUseCase

    @Before
    fun setup() {
        repo = FakeFinanceRepository()
        useCase = UpsertCategoryUseCase(repo)
    }

    @Test
    fun `blank name is rejected`() = runTest {
        try {
            useCase(Category(name = "", type = TransactionType.EXPENSE))
            fail("Expected IllegalArgumentException for empty name")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("分类名"))
        }
        try {
            useCase(Category(name = "   ", type = TransactionType.EXPENSE))
            fail("Expected IllegalArgumentException for whitespace name")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `id zero means insert and returns new positive id`() = runTest {
        val id = useCase(
            Category(
                id = 0L,
                name = "餐饮",
                type = TransactionType.EXPENSE,
            )
        )
        assertTrue("expected id > 0, got $id", id > 0L)
        val list = repo.observeCategories().first()
        assertEquals(1, list.size)
        assertEquals("餐饮", list[0].name)
    }

    @Test
    fun `name is trimmed before persistence`() = runTest {
        useCase(Category(id = 0L, name = "  餐饮  ", type = TransactionType.EXPENSE))
        val list = repo.observeCategories().first()
        assertEquals("餐饮", list[0].name)
    }

    @Test
    fun `id positive means update and keeps same id`() = runTest {
        val firstId = useCase(Category(id = 0L, name = "餐饮", type = TransactionType.EXPENSE))
        val secondId = useCase(
            Category(
                id = firstId,
                name = "吃饭",
                type = TransactionType.EXPENSE,
            )
        )
        assertEquals(firstId, secondId)
        val list = repo.observeCategories().first()
        assertEquals(1, list.size)
        assertEquals("吃饭", list[0].name)
        assertEquals(firstId, list[0].id)
    }

    @Test
    fun `income category can be persisted`() = runTest {
        useCase(Category(id = 0L, name = "工资", type = TransactionType.INCOME))
        val list = repo.observeCategoriesByType(TransactionType.INCOME).first()
        assertEquals(1, list.size)
        assertEquals(TransactionType.INCOME, list[0].type)
    }
}
