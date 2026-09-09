package com.example.personalassistant.feature.finance.data.repository

import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.CategoryInUseException
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Phase 7a：单元测试用的内存版 [FinanceRepository]。
 *
 * ## 设计动机
 * - UseCase 测试要快、要不依赖 Room
 * - 不模拟完整业务算法（refreshDailyPool 只留空实现，复杂逻辑交给 RepositoryImpl 单测）
 * - 只保证接口契约 + 状态变更可见
 *
 * ## 约定
 * - 所有 suspend 写操作同步更新内存 + 触发 flow 重新发射
 * - id 自增（nextId 从 1 开始）
 */
class FakeFinanceRepository : FinanceRepository {

    private val categoriesState = MutableStateFlow<List<Category>>(emptyList())
    private val transactionsState = MutableStateFlow<List<Transaction>>(emptyList())
    private val budgetsState = MutableStateFlow<List<MonthlyBudget>>(emptyList())
    private val dailyPoolsState = MutableStateFlow<List<DailyPool>>(emptyList())

    private var nextCategoryId = 1L
    private var nextTxId = 1L
    private var nextBudgetId = 1L
    private var nextPoolId = 1L

    /** 记录 refreshDailyPool 调用次数（用于 RefreshDailyPoolUseCaseTest 断言） */
    var refreshDailyPoolCallCount: Int = 0
        private set
    /** 记录最近一次 refreshDailyPool 调用的 today 参数 */
    var lastRefreshToday: Long = 0L
        private set

    // ===== Transactions =====

    override suspend fun addTransaction(
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
        attachmentUri: String?,
    ): Long {
        val id = nextTxId++
        transactionsState.value = transactionsState.value + Transaction(
            id = id,
            amount = amount,
            type = type,
            categoryId = categoryId,
            date = date,
            note = note,
            createdAt = System.currentTimeMillis(),
            source = "手动",
            attachmentUri = attachmentUri,
        )
        return id
    }

    override suspend fun deleteTransaction(id: Long) {
        transactionsState.value = transactionsState.value.filter { it.id != id }
    }

    /**
     * 7b 新增：编辑流水。
     * 模拟 RepositoryImpl：找出旧 tx，用新字段替换（id 不变）。
     * 不模拟 pool 同步（Fake 不承担算法测试）。
     */
    override suspend fun updateTransaction(
        id: Long,
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
        attachmentUri: String?,
    ) {
        transactionsState.value = transactionsState.value.map {
            if (it.id == id) it.copy(
                amount = amount,
                type = type,
                categoryId = categoryId,
                note = note,
                date = date,
                attachmentUri = attachmentUri,
            ) else it
        }
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> = transactionsState

    override fun observeTransactionsBetween(start: Long, end: Long): Flow<List<Transaction>> =
        transactionsState.map { list -> list.filter { it.date in start until end } }

    override fun observeByTypeBetween(
        type: TransactionType,
        start: Long,
        end: Long,
    ): Flow<List<Transaction>> = transactionsState.map { list ->
        list.filter { it.type == type && it.date in start until end }
    }

    /** 7b 新增：按关键字搜索（Fake 在内存里做简单子串匹配，不依赖分类名——7a 分类未落库） */
    override fun searchTransactions(keyword: String): Flow<List<Transaction>> =
        transactionsState.map { list ->
            if (keyword.isBlank()) list
            else list.filter { tx ->
                tx.note?.contains(keyword, ignoreCase = true) == true
            }
        }

    // ===== Categories =====

    override fun observeCategories(): Flow<List<Category>> = categoriesState

    override fun observeCategoriesByType(type: TransactionType): Flow<List<Category>> =
        categoriesState.map { list -> list.filter { it.type == type } }

    override suspend fun upsertCategory(category: Category): Long {
        val list = categoriesState.value
        return if (category.id == 0L) {
            val id = nextCategoryId++
            categoriesState.value = list + category.copy(id = id)
            id
        } else {
            categoriesState.value = list.map { if (it.id == category.id) category else it }
            category.id
        }
    }

    override suspend fun deleteCategory(id: Long) {
        val used = transactionsState.value.any { it.categoryId == id }
        if (used) throw CategoryInUseException(id, transactionsState.value.count { it.categoryId == id })
        categoriesState.value = categoriesState.value.filter { it.id != id }
    }

    // ===== Monthly Budget =====

    override fun observeCurrentMonthBudget(): Flow<MonthlyBudget?> {
        val currentYm = currentYearMonth()
        return budgetsState.map { list -> list.firstOrNull { it.yearMonth == currentYm } }
    }

    override fun observeAllBudgets(): Flow<List<MonthlyBudget>> = budgetsState

    override suspend fun setMonthlyBudget(
        yearMonth: String,
        totalAmount: Double,
        isAutoRefresh: Boolean,
        carryOverEnabled: Boolean,
        warningThreshold: Double,
    ): Long {
        val list = budgetsState.value
        val existing = list.firstOrNull { it.yearMonth == yearMonth }
        val now = System.currentTimeMillis()
        return if (existing == null) {
            val id = nextBudgetId++
            budgetsState.value = list + MonthlyBudget(
                id = id,
                yearMonth = yearMonth,
                totalAmount = totalAmount,
                isAutoRefresh = isAutoRefresh,
                carryOverEnabled = carryOverEnabled,
                warningThreshold = warningThreshold,
                createdAt = now,
                updatedAt = now,
            )
            id
        } else {
            budgetsState.value = list.map {
                if (it.id == existing.id) it.copy(
                    totalAmount = totalAmount,
                    isAutoRefresh = isAutoRefresh,
                    carryOverEnabled = carryOverEnabled,
                    warningThreshold = warningThreshold,
                    updatedAt = now,
                ) else it
            }
            existing.id
        }
    }

    // ===== Daily Pool =====

    override fun observeTodayPool(): Flow<DailyPool?> {
        val todayStart = todayStartMillis()
        return dailyPoolsState.map { list -> list.firstOrNull { it.date == todayStart } }
    }

    override suspend fun refreshDailyPool(today: Long) {
        refreshDailyPoolCallCount += 1
        lastRefreshToday = today
        // Fake 简单实现：当日 pool 不存在则创建占位记录（确保 flow 可观察）。
        val dayStart = startOfDay(today)
        val exists = dailyPoolsState.value.any { it.date == dayStart }
        if (!exists) {
            val id = nextPoolId++
            dailyPoolsState.value = dailyPoolsState.value + DailyPool(
                id = id,
                date = dayStart,
                dailyBudget = 0.0,
                remainingAmount = 0.0,
                lastUpdated = System.currentTimeMillis(),
            )
        }
    }

    // ===== 测试辅助 =====

    fun reset() {
        categoriesState.value = emptyList()
        transactionsState.value = emptyList()
        budgetsState.value = emptyList()
        dailyPoolsState.value = emptyList()
        nextCategoryId = 1L
        nextTxId = 1L
        nextBudgetId = 1L
        nextPoolId = 1L
        refreshDailyPoolCallCount = 0
        lastRefreshToday = 0L
    }

    private fun currentYearMonth(): String =
        java.time.LocalDate.now().let { "%04d-%02d".format(it.year, it.monthValue) }

    private fun todayStartMillis(): Long = startOfDay(System.currentTimeMillis())

    private fun startOfDay(epochMillis: Long): Long {
        val zone = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    // ===== 测试辅助 helpers（7b 加） =====

    /** 快速建一个支出分类，返回 id */
    suspend fun seedExpenseCategory(name: String): Long {
        val id = nextCategoryId++
        categoriesState.value = categoriesState.value + Category(
            id = id,
            name = name,
            type = TransactionType.EXPENSE,
            iconName = "Restaurant",
            color = 0xFFE91E63.toInt(),
        )
        return id
    }

    /** 快速建一笔交易（走 Repository 接口等价路径），返回 id */
    suspend fun seedTransaction(
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
    ): Long {
        return addTransaction(type, amount, categoryId, note, date, attachmentUri = null)
    }
}
