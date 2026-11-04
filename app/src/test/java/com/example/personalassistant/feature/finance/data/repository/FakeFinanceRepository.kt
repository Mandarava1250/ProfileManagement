package com.example.personalassistant.feature.finance.data.repository

import com.example.personalassistant.feature.finance.domain.model.AccountBalanceSum
import com.example.personalassistant.feature.finance.domain.model.AccountBudgetSummary
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.CoinPool
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.GlobalMonthlySums
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.CategoryInUseException
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.finance.domain.repository.TransferNotAllowedException
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
    private val coinPoolsState = MutableStateFlow<List<CoinPool>>(emptyList())
    private var nextCoinPoolId = 1L

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

    /**
     * 转账流水 id → pairId（Phase 5）。
     *
     * 为什么不放进领域模型：pairId 只服务「删一条要连带删另一条」，属于存储细节，
     * 放进 [Transaction] 等于让 UI 层多一个永远用不到、还可能误用的字段。
     * 所以真库存在 Entity 列里，Fake 存在这张表里。
     */
    private val pairIdByTxId = mutableMapOf<Long, String>()

    /**
     * 跨账户汇总读的可编程桩（Phase 5）。
     *
     * 这三个**不自己算**：真值是 DAO 聚合 SQL 的结果，在 JVM 里算等于
     * 把 SQL 用 Kotlin 重写一遍再测自己——测试会绿得毫无意义。
     * 所以这里直接让测试喂数据，只用来测上层（UseCase / ViewModel / UI 派生字段）。
     * SQL 本身的正确归 androidTest（真 SQLite）管。
     */
    val budgetSummariesState = MutableStateFlow<List<AccountBudgetSummary>>(emptyList())
    val balanceSumsState = MutableStateFlow<List<AccountBalanceSum>>(emptyList())
    val globalMonthlySumsState = MutableStateFlow<GlobalMonthlySums?>(null)

    // ===== Transactions =====

    override suspend fun addTransaction(
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
        attachmentUri: String?,
        linkedTaskId: Long?,
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
            source = if (linkedTaskId != null) "清单" else "手动",
            attachmentUri = attachmentUri,
            // Phase 21：来源清单项
            linkedTaskId = linkedTaskId,
        )
        return id
    }

    override suspend fun deleteTransaction(id: Long) {
        // Phase 5 跟真实现一致：转账不得走普通删除。
        // 这条守卫住在 Fake 里是有意义的：上层 UseCase 测试可以验证
        // 「拿到转账流水时它选了正确的仓库方法」而不只是验证并集行为。
        val target = transactionsState.value.firstOrNull { it.id == id }
        if (target != null && target.type.isTransfer) {
            throw TransferNotAllowedException(
                TransferNotAllowedException.Reason.TRANSFER_NEEDS_PAIR_DELETE,
                "转账流水请走 deleteTransfer()",
            )
        }
        transactionsState.value = transactionsState.value.filter { it.id != id }
        pairIdByTxId.remove(id)
    }

    // ===== Phase 5：跨账户转账 =====

    /**
     * 真实成对写入（不像上面三个汇总那样做桩）。
     *
     * 因为「一笔转账 = 两行」就是本轮要测的核心不变量；
     * Fake 若只写一行，那所有关于配对的测试都在测一个假事实。
     *
     * Fake 不区分账户（单账本内存模型），所以这里**不校验余额与账户存在性** ——
     * 那些是 RepositoryImpl 的职责，由它的单测与 androidTest 覆盖。
     */
    override suspend fun transfer(
        toAccountId: Long,
        amount: Double,
        note: String?,
        date: Long,
        allowNegativeBalance: Boolean,
    ): Long {
        if (amount <= 0.0) {
            throw TransferNotAllowedException(
                TransferNotAllowedException.Reason.INVALID_AMOUNT,
                "转账金额必须大于 0",
            )
        }
        val pairId = "pair-${nextTxId}"
        val outId = nextTxId++
        val inId = nextTxId++
        val trimmed = note?.trim()?.takeIf { it.isNotEmpty() }
        val base = Transaction(
            id = outId,
            amount = amount,
            type = TransactionType.TRANSFER_OUT,
            categoryId = TRANSFER_CATEGORY_ID,
            date = date,
            note = trimmed,
            createdAt = System.currentTimeMillis(),
            source = "转账",
            transferAccountId = toAccountId,
        )
        pairIdByTxId[outId] = pairId
        pairIdByTxId[inId] = pairId
        transactionsState.value = transactionsState.value +
            base +
            base.copy(
                id = inId,
                type = TransactionType.TRANSFER_IN,
                transferAccountId = CURRENT_ACCOUNT_ID,
            )
        transferCallCount += 1
        lastTransferToAccountId = toAccountId
        return outId
    }

    /** 转账调用次数与最近目标账户（供上层断言「真的只调了一次」） */
    var transferCallCount: Int = 0
        private set
    var lastTransferToAccountId: Long? = null
        private set

    override suspend fun deleteTransfer(id: Long): Int {
        val pairId = pairIdByTxId[id] ?: return 0
        val victims = pairIdByTxId.filterValues { it == pairId }.keys
        transactionsState.value = transactionsState.value.filter { it.id !in victims }
        pairIdByTxId.keys.removeAll(victims)
        return victims.size
    }

    override fun observeAccountBudgetSummaries(): Flow<List<AccountBudgetSummary>> =
        budgetSummariesState

    override fun observeBalanceSums(): Flow<List<AccountBalanceSum>> = balanceSumsState

    override fun observeGlobalMonthlySums(): Flow<GlobalMonthlySums?> = globalMonthlySumsState

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
        warningThreshold: Double?,
    ): Long {
        // null 落 0.0（与 Impl 一致；详见 SetMonthlyBudgetUseCase.invoke 的 nullable 重载）
        val stored = warningThreshold ?: 0.0
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
                warningThreshold = stored,
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
                    warningThreshold = stored,
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

    // ===== 批次 C：零钱池 =====

    override fun observeCoinPool(): Flow<CoinPool?> =
        coinPoolsState.map { list -> list.firstOrNull() }

    override suspend fun adjustCoinPool(delta: Double, note: String?): Double {
        val existing = coinPoolsState.value.firstOrNull()
        val newBalance = (existing?.balance ?: 0.0) + delta
        writeCoinPool(newBalance, note, existing)
        return newBalance
    }

    override suspend fun setCoinPoolBalance(balance: Double, note: String?): Double {
        writeCoinPool(balance, note, coinPoolsState.value.firstOrNull())
        return balance
    }

    private fun writeCoinPool(balance: Double, note: String?, existing: CoinPool?) {
        val now = System.currentTimeMillis()
        coinPoolsState.value = if (existing == null) {
            listOf(
                CoinPool(
                    id = nextCoinPoolId++,
                    accountId = CURRENT_ACCOUNT_ID,
                    balance = balance,
                    note = note,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        } else {
            listOf(
                existing.copy(
                    balance = balance,
                    note = note ?: existing.note,
                    updatedAt = now,
                )
            )
        }
    }

    // ===== 测试辅助 =====

    fun reset() {
        categoriesState.value = emptyList()
        transactionsState.value = emptyList()
        budgetsState.value = emptyList()
        dailyPoolsState.value = emptyList()
        coinPoolsState.value = emptyList()
        nextCoinPoolId = 1L
        budgetSummariesState.value = emptyList()
        balanceSumsState.value = emptyList()
        globalMonthlySumsState.value = null
        pairIdByTxId.clear()
        nextCategoryId = 1L
        nextTxId = 1L
        nextBudgetId = 1L
        nextPoolId = 1L
        refreshDailyPoolCallCount = 0
        transferCallCount = 0
        lastTransferToAccountId = null
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

    /**
     * 快速建一笔交易（走 Repository 接口等价路径），返回 id
     *
     * ⚠️ Phase 5：本方法**不能**传转账类型。转账必须成对写入，
     * 单传一行会让下游测试跑在一个真库永远不会出现的数据形状上。
     */
    suspend fun seedTransaction(
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
    ): Long {
        require(!type.isTransfer) {
            "seedTransaction 不支持转账类型：请改用 transfer()，它会成对写入"
        }
        return addTransaction(type, amount, categoryId, note, date, attachmentUri = null)
    }

    private companion object {
        /** Fake 里的转账分类占位 id（真库靠查表拿，Fake 不需要外键） */
        const val TRANSFER_CATEGORY_ID = -2L

        /** Fake 单账本模型下「当前账户」的固定 id，仅用于填 transferAccountId */
        const val CURRENT_ACCOUNT_ID = 1L
    }
}
