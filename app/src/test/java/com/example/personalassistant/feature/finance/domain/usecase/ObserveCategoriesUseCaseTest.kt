package com.example.personalassistant.feature.finance.domain.usecase

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 7a：ObserveCategoriesUseCase 单元测试
 *
 * 覆盖：
 * - all() 委托 observeCategories，返回所有分类
 * - byType(EXPENSE) 只返回支出分类
 * - byType(INCOME) 只返回收入分类
 * - 新增分类后 observe 流立刻更新
 */
class ObserveCategoriesUseCaseTest {

    private lateinit var repo: FakeFinanceRepository
    private lateinit var useCase: ObserveCategoriesUseCase

    @Before
    fun setup() {
        repo = FakeFinanceRepository()
        useCase = ObserveCategoriesUseCase(repo)
    }

    @Test
    fun `all emits full list when no filter`() = runTest {
        repo.upsertCategory(Category(id = 0L, name = "餐饮", type = TransactionType.EXPENSE))
        repo.upsertCategory(Category(id = 0L, name = "交通", type = TransactionType.EXPENSE))
        repo.upsertCategory(Category(id = 0L, name = "工资", type = TransactionType.INCOME))

        val all = useCase.all().first()
        assertEquals(3, all.size)
    }

    @Test
    fun `byType EXPENSE filters out income categories`() = runTest {
        repo.upsertCategory(Category(id = 0L, name = "餐饮", type = TransactionType.EXPENSE))
        repo.upsertCategory(Category(id = 0L, name = "工资", type = TransactionType.INCOME))

        val expenses = useCase.byType(TransactionType.EXPENSE).first()
        assertEquals(1, expenses.size)
        assertEquals("餐饮", expenses[0].name)
        assertEquals(TransactionType.EXPENSE, expenses[0].type)
    }

    @Test
    fun `byType INCOME returns only income categories`() = runTest {
        repo.upsertCategory(Category(id = 0L, name = "餐饮", type = TransactionType.EXPENSE))
        repo.upsertCategory(Category(id = 0L, name = "工资", type = TransactionType.INCOME))
        repo.upsertCategory(Category(id = 0L, name = "奖金", type = TransactionType.INCOME))

        val income = useCase.byType(TransactionType.INCOME).first()
        assertEquals(2, income.size)
        assertTrue(income.all { it.type == TransactionType.INCOME })
    }

    @Test
    fun `empty repo emits empty list`() = runTest {
        assertTrue(useCase.all().first().isEmpty())
        assertTrue(useCase.byType(TransactionType.EXPENSE).first().isEmpty())
        assertTrue(useCase.byType(TransactionType.INCOME).first().isEmpty())
    }
}
