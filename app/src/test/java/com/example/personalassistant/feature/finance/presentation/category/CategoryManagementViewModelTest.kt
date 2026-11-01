package com.example.personalassistant.feature.finance.presentation.category

import com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.CategoryInUseException
import com.example.personalassistant.feature.finance.domain.usecase.DeleteCategoryUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCategoriesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.UpsertCategoryUseCase
import com.example.personalassistant.feature.finance.presentation.util.Feedback
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
 *
 * C 重构阶段 5/11 新增：
 * - feedback 现在是 Feedback 类型（Success/Warning/Error），断言改为类型检查
 * - addCategory 4 参数签名（name/type/iconName/color）
 * - rename：成功并写 feedback
 * - changeColor：成功并写 feedback
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
        vm.addCategory("奶茶", TransactionType.EXPENSE, "Restaurant", 0xFFE57373.toInt())
        advanceUntilIdle()
        val list = repo.observeCategories().first()
        assertEquals(1, list.size)
        assertEquals("奶茶", list[0].name)
        assertEquals(0xFFE57373.toInt(), list[0].color)
        // C 阶段 5：feedback 应当是 Success
        val fb = vm.feedback.value
        assertNotNull(fb)
        assertTrue("feedback 应当是 Success, got $fb", fb is Feedback.Success)
    }

    @Test
    fun `deleteCategory succeeds for unused category`() = runTest(testDispatcher) {
        repo.seedExpenseCategory("临时分类")
        val cat = repo.observeCategories().first().first()

        vm.deleteCategory(cat)
        advanceUntilIdle()
        assertTrue(repo.observeCategories().first().isEmpty())
        val fb = vm.feedback.value
        assertNotNull(fb)
        assertTrue(fb is Feedback.Success)
        assertTrue((fb as Feedback.Success).message.contains("已删除"))
    }

    @Test
    fun `deleteCategory surfaces in-use error`() = runTest(testDispatcher) {
        val catId = repo.seedExpenseCategory("有引用的")
        repo.seedTransaction(TransactionType.EXPENSE, 10.0, catId, null, 1000L)
        val cat = repo.observeCategories().first().first()

        vm.deleteCategory(cat)
        advanceUntilIdle()
        assertTrue("Category should still exist", repo.observeCategories().first().any { it.id == catId })
        // 业务上"还能被引用"是 Warning，不是 Error
        val fb = vm.feedback.value
        assertNotNull(fb)
        assertTrue("feedback 应当是 Warning, got $fb", fb is Feedback.Warning)
        assertTrue((fb as Feedback.Warning).message.contains("无法删除"))
    }

    @Test
    fun `setIcon replaces iconName without changing other fields`() = runTest(testDispatcher) {
        repo.seedExpenseCategory("c")
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
        advanceUntilIdle()
        assertEquals(emptyList<Category>(), vm.categories.value)

        repo.seedExpenseCategory("A")
        advanceUntilIdle()
        val list = repo.observeCategories().first()
        assertEquals(1, list.size)
        assertEquals("A", list[0].name)
    }

    /**
     * C 阶段 5：rename 改名字，其它字段不变，写 Success feedback。
     */
    @Test
    fun `rename updates name and writes Success feedback`() = runTest(testDispatcher) {
        val catId = repo.seedExpenseCategory("原名")
        val original = repo.observeCategories().first().first()

        vm.rename(original, "新名字")
        advanceUntilIdle()

        val updated = repo.observeCategories().first().first { it.id == catId }
        assertEquals("新名字", updated.name)
        assertEquals(original.color, updated.color)
        assertEquals(original.iconName, updated.iconName)
        val fb = vm.feedback.value
        assertTrue(fb is Feedback.Success)
    }

    /**
     * C 阶段 5：rename 传空字符串 → Warning feedback，不写 repo。
     */
    @Test
    fun `rename with blank name emits Warning feedback and does not write`() = runTest(testDispatcher) {
        val catId = repo.seedExpenseCategory("保留")
        val original = repo.observeCategories().first().first()

        vm.rename(original, "   ")
        advanceUntilIdle()

        // 没写入
        val after = repo.observeCategories().first().first { it.id == catId }
        assertEquals("保留", after.name)
        val fb = vm.feedback.value
        assertTrue("应当是 Warning, got $fb", fb is Feedback.Warning)
        assertTrue((fb as Feedback.Warning).message.contains("空"))
    }

    /**
     * C 阶段 5：changeColor 改颜色，写 Success feedback。
     */
    @Test
    fun `changeColor updates color and writes Success feedback`() = runTest(testDispatcher) {
        repo.seedExpenseCategory("c")
        val original = repo.observeCategories().first().first()
        val newColor = 0xFFE57373.toInt()
        assertTrue(original.color != newColor)

        vm.changeColor(original, newColor)
        advanceUntilIdle()

        val updated = repo.observeCategories().first().first { it.name == "c" }
        assertEquals(newColor, updated.color)
        assertEquals(original.name, updated.name)
        val fb = vm.feedback.value
        assertTrue(fb is Feedback.Success)
    }
}