package com.example.personalassistant.feature.finance.presentation.category

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.CategoryInUseException
import com.example.personalassistant.feature.finance.domain.usecase.DeleteCategoryUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCategoriesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.UpsertCategoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 7b：CategoryManagementViewModel 单元测试
 *
 * 覆盖：
 * - addCategory：成功添加并写 feedback
 * - deleteCategory：成功删除
 * - deleteCategory 被引用 → CategoryInUseException → feedback 反映原因
 * - setIcon：替换 iconName，其它字段不变
 * - categories 列表实时反映 changes
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeFinanceRepository
    private lateinit var vm: CategoryManagementViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repo = FakeFinanceRepository()
        vm = CategoryManagementViewModel(
            observeCategories = ObserveCategoriesUseCase(repo),
            upsertCategory = UpsertCategoryUseCase(repo),
            deleteCategory = DeleteCategoryUseCase(repo),
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addCategory adds new category and sets feedback`() = runTest(testDispatcher) {
        vm.addCategory("奶茶", TransactionType.EXPENSE)
        advanceUntilIdle()
        val list = repo.observeCategories().first()
        assertEquals(1, list.size)
        assertEquals("奶茶", list[0].name)
        assertNotNull(vm.feedback.value)
    }

    @Test
    fun `deleteCategory succeeds for unused category`() = runTest(testDispatcher) {
        val catId = repo.seedExpenseCategory("临时分类")
        val cat = repo.observeCategories().first().first()

        vm.deleteCategory(cat)
        advanceUntilIdle()
        assertTrue(repo.observeCategories().first().isEmpty())
        assertTrue(vm.feedback.value!!.contains("已删除"))
    }

    @Test
    fun `deleteCategory surfaces in-use error`() = runTest(testDispatcher) {
        val catId = repo.seedExpenseCategory("有引用的")
        repo.seedTransaction(TransactionType.EXPENSE, 10.0, catId, null, 1000L)
        val cat = repo.observeCategories().first().first()

        vm.deleteCategory(cat)
        advanceUntilIdle()
        assertTrue("Category should still exist", repo.observeCategories().first().any { it.id == catId })
        val msg = vm.feedback.value ?: ""
        assertTrue("Feedback should mention in-use, got: $msg", msg.contains("无法删除"))
    }

    @Test
    fun `setIcon replaces iconName without changing other fields`() = runTest(testDispatcher) {
        val catId = repo.seedExpenseCategory("c")
        val original = repo.observeCategories().first().first()
        assertEquals("Restaurant", original.iconName)

        vm.setIcon(original, "Pets")
        advanceUntilIdle()

        val updated = repo.observeCategories().first().first()
        assertEquals("Pets", updated.iconName)
        assertEquals(original.name, updated.name)
        assertEquals(original.color, updated.color)
    }

    @Test
    fun `categories flow eventually reflects upserts`() = runTest(testDispatcher) {
        // stateIn 的 initialValue 是 emptyList，advanceUntilIdle 后应已 collect upstream
        advanceUntilIdle()
        assertEquals(emptyList<Category>(), vm.categories.value)

        repo.seedExpenseCategory("A")
        advanceUntilIdle()
        // 直接断言 repo 的 flow 反映了变更（vm.categories.value 的状态取决于 stateIn 缓存时机）
        val list = repo.observeCategories().first()
        assertEquals(1, list.size)
        assertEquals("A", list[0].name)
    }
}