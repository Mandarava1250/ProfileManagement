package com.example.personalassistant.feature.finance.data.repository

import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.finance.data.mapper.toDomain
import com.example.personalassistant.feature.finance.data.mapper.toEntity
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.CategoryInUseException
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.profile.data.CurrentAccountRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7a：记账仓库实现
 *
 * ## 设计要点
 * - 所有 IO 走 [dispatcher]（IoDispatcher）
 * - 写入流水走 FinanceDao 的事务方法（recordExpense / recordIncome / deleteTransactionAndRollbackPool），
 *   保证 transactions + daily_pools 的原子性
 * - refreshDailyPool 完整实现说明书 5.3.2 零钱池算法（涟宝解读版）：
 *   1. 取当月预算；不存在 → no-op
 *   2. 取当月已支出
 *   3. dailyBudget = (totalAmount - 已支出) / 当月剩余天数（含今日，最少 1 天）
 *   4. carryOver = carryOverEnabled ? max(0, 昨日 pool.remainingAmount) : 0
 *   5. 幂等：当日 pool 已存在 → no-op
 *   6. 不存在 → 新建 pool（remainingAmount = dailyBudget + carryOver）
 *
 * ## Phase 3：账户隔离怎么落在这里（接口签名一个都没改）
 * - **写路径**（suspend）：先 `awaitAccountId()` 取快照，再 `entity.copy(accountId = ...)`；
 *   DAO 的事务方法一律从 entity 的 accountId 派生零钱池归属。
 * - **读路径**（Flow）：统一 `accountIdFlow.flatMapLatest { dao.query(it, ...) }`。
 *   切账户 → DataStore 发新值 → 整条读流重订阅 → 记账页/预算/零钱池/搜索结果自动换新账本，
 *   UI 侧不需要任何手动 refresh。
 *
 * ⚠️ 读路径**只能**走 [accountIdFlow]。历史教训：这版初稿曾用
 * `flowOf(常量)` 包一层「快照」，结果读路径永远停在默认账户 —— 编译和现有单测都发现不了，
 * 表现为静默错账。新增任何 observe/search 方法时，务必 flatMapLatest 到 [accountIdFlow]。
 */
@Singleton
class FinanceRepositoryImpl @Inject constructor(
    private val financeDao: FinanceDao,
    private val currentAccountRepository: CurrentAccountRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : FinanceRepository {

    /** 当前账户 id 的读源头：订阅即建立，切账户即重发 */
    private val accountIdFlow: Flow<Long> = currentAccountRepository.currentAccountIdFlow()

    // ===================== 流水 =====================

    override suspend fun addTransaction(
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
        attachmentUri: String?,
    ): Long = withContext(dispatcher) {
        val accountId = awaitAccountId()
        val poolDate = startOfDayMillis(date)
        val txEntity = TransactionEntity(
            id = 0L,
            accountId = accountId,
            amount = amount,
            type = type.storageValue,
            categoryId = categoryId,
            date = date,
            note = note,
            createdAt = System.currentTimeMillis(),
            source = "手动",
            attachmentUri = attachmentUri,
        )
        when (type) {
            TransactionType.EXPENSE ->
                financeDao.recordExpense(txEntity, poolDate, amount)
            TransactionType.INCOME ->
                financeDao.recordIncome(txEntity, poolDate, amount)
        }
    }

    override suspend fun deleteTransaction(id: Long) = withContext(dispatcher) {
        val accountId = awaitAccountId()
        val tx = financeDao.getTransactionById(accountId, id) ?: return@withContext
        val poolDate = startOfDayMillis(tx.date)
        financeDao.deleteTransactionAndRollbackPool(tx, poolDate)
    }

    override suspend fun updateTransaction(
        id: Long,
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
        attachmentUri: String?,
    ): Unit = withContext(dispatcher) {
        val accountId = awaitAccountId()
        // 1. 取旧 tx（带 accountId 守卫：别的账户的同 id 流水改不到）；不存在 → no-op
        val oldTx = financeDao.getTransactionById(accountId, id) ?: return@withContext
        // 2. 构造新 tx（保留 createdAt / source / accountId，便于审计与归属）
        val newTx = oldTx.copy(
            amount = amount,
            type = type.storageValue,
            categoryId = categoryId,
            date = date,
            note = note,
            attachmentUri = attachmentUri,
        )
        // 3. 走 DAO 事务方法：退旧 pool + 应用新 pool
        financeDao.transactionalUpdateTransaction(oldTx, newTx)
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> =
        accountIdFlow.flatMapLatest { accountId ->
            financeDao.observeAllTransactions(accountId)
        }.map { list -> list.map { it.toDomain() } }

    override fun observeTransactionsBetween(start: Long, end: Long): Flow<List<Transaction>> =
        accountIdFlow.flatMapLatest { accountId ->
            financeDao.observeBetween(accountId, start, end)
        }.map { list -> list.map { it.toDomain() } }

    override fun observeByTypeBetween(
        type: TransactionType,
        start: Long,
        end: Long,
    ): Flow<List<Transaction>> =
        accountIdFlow.flatMapLatest { accountId ->
            financeDao.observeByTypeBetween(accountId, type.storageValue, start, end)
        }.map { list -> list.map { it.toDomain() } }

    override fun searchTransactions(keyword: String): Flow<List<Transaction>> =
        accountIdFlow.flatMapLatest { accountId ->
            financeDao.searchTransactions(accountId, keyword)
        }.map { list -> list.map { it.toDomain() } }

    // ===================== 分类 =====================

    override fun observeCategories(): Flow<List<Category>> =
        accountIdFlow.flatMapLatest { accountId ->
            financeDao.observeAllCategories(accountId)
        }.map { list -> list.map { it.toDomain() } }

    override fun observeCategoriesByType(type: TransactionType): Flow<List<Category>> =
        accountIdFlow.flatMapLatest { accountId ->
            financeDao.observeCategoriesByType(accountId, type.storageValue)
        }.map { list -> list.map { it.toDomain() } }

    override suspend fun upsertCategory(category: Category): Long = withContext(dispatcher) {
        val accountId = awaitAccountId()
        // domain Category 不带 accountId（对 UI 透明），归属在这里注入
        financeDao.insertCategory(category.toEntity().copy(accountId = accountId))
    }

    override suspend fun deleteCategory(id: Long): Unit = withContext(dispatcher) {
        val accountId = awaitAccountId()
        // 1. 检查引用数（按账户统计：别的账户引用同一 categoryId 不该挡住本账户删分类）
        val count = financeDao.countTransactionsByCategory(accountId, id)
        if (count > 0) {
            throw CategoryInUseException(categoryId = id, transactionCount = count)
        }
        // 2. 删除（带 accountId 守卫）
        val entity = financeDao.getCategoryById(accountId, id) ?: return@withContext
        financeDao.deleteCategory(entity)
    }

    // ===================== 月度预算 =====================

    override fun observeCurrentMonthBudget(): Flow<MonthlyBudget?> =
        accountIdFlow.flatMapLatest { accountId ->
            financeDao.observeMonthlyBudget(accountId, currentYearMonth())
        }.map { it?.toDomain() }

    override fun observeAllBudgets(): Flow<List<MonthlyBudget>> =
        accountIdFlow.flatMapLatest { accountId ->
            financeDao.observeAllBudgets(accountId)
        }.map { list -> list.map { it.toDomain() } }

    override suspend fun setMonthlyBudget(
        yearMonth: String,
        totalAmount: Double,
        isAutoRefresh: Boolean,
        carryOverEnabled: Boolean,
        warningThreshold: Double,
    ): Long = withContext(dispatcher) {
        val accountId = awaitAccountId()
        val now = System.currentTimeMillis()
        val existing = financeDao.getMonthlyBudget(accountId, yearMonth)
        val entity = MonthlyBudgetEntity(
            id = existing?.id ?: 0L,
            accountId = accountId,
            yearMonth = yearMonth,
            totalAmount = totalAmount,
            isAutoRefresh = isAutoRefresh,
            carryOverEnabled = carryOverEnabled,
            warningThreshold = warningThreshold,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        financeDao.setMonthlyBudget(entity)
        // 如果修改的是当月预算，触发立即刷新（不等 Worker）
        if (yearMonth == currentYearMonth()) {
            refreshDailyPool(System.currentTimeMillis())
        }
        entity.id.takeIf { it > 0 } ?: now  // insert 后 id 不会变（Room 用 rowId）
    }

    // ===================== 零钱池 =====================

    override fun observeTodayPool(): Flow<DailyPool?> =
        accountIdFlow.flatMapLatest { accountId ->
            financeDao.observeDailyPool(accountId, startOfDayMillis(System.currentTimeMillis()))
        }.map { it?.toDomain() }

    /**
     * Phase 3：算法的 3 处数据读取全部按当前账户 ——
     * 预算读取、当月支出统计、昨日 pool 结转。
     * 漏任一处都会让 A 账户的支出吃掉 B 账户的日预算（静默错账，比崩溃更难发现）。
     */
    override suspend fun refreshDailyPool(today: Long) = withContext(dispatcher) {
        val accountId = awaitAccountId()
        val todayPoolDate = startOfDayMillis(today)
        val ym = yearMonthOf(today)
        val zone = ZoneId.systemDefault()

        // 1. 取当月预算；不存在 → no-op
        val budget = financeDao.getMonthlyBudget(accountId, ym) ?: return@withContext

        // 2. 取当月已支出（含今日）
        val monthStart = YearMonth.from(ymLocalDate(ym)).atDay(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        // monthEndExclusive = 下月 1 号 00:00
        val monthEndExclusive = YearMonth.from(ymLocalDate(ym)).plusMonths(1)
            .atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthExpense = financeDao.getMonthlyExpense(
            accountId = accountId,
            monthStart = monthStart,
            monthEnd = monthEndExclusive,
        ) ?: 0.0

        // 3. dailyBudget = (totalAmount - 已支出) / 当月剩余天数（含今日，最少 1 天）
        val todayDate = Instant.ofEpochMilli(today).atZone(zone).toLocalDate()
        val ymObj = YearMonth.from(todayDate)
        val daysInMonth = ymObj.lengthOfMonth()
        val dayOfMonth = todayDate.dayOfMonth
        val remainingDays = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)
        val remainingAmount = (budget.totalAmount - monthExpense).coerceAtLeast(0.0)
        val dailyBudget = remainingAmount / remainingDays

        // 4. carryOver：取昨日 pool.remainingAmount（>=0 才结转）
        val carryOver = if (budget.carryOverEnabled) {
            val yesterdayPoolDate = todayPoolDate - ONE_DAY_MS
            financeDao.getDailyPool(accountId, yesterdayPoolDate)
                ?.remainingAmount?.coerceAtLeast(0.0) ?: 0.0
        } else {
            0.0
        }

        // 5. 当日 pool 已存在 → 幂等跳过
        if (financeDao.getDailyPool(accountId, todayPoolDate) != null) return@withContext

        // 6. 创建今日 pool
        financeDao.insertDailyPool(
            DailyPoolEntity(
                id = 0L,
                accountId = accountId,
                date = todayPoolDate,
                dailyBudget = dailyBudget,
                remainingAmount = dailyBudget + carryOver,
                totalSpentToday = 0.0,
                totalIncomeToday = 0.0,
                carryOver = carryOver,
                lastUpdated = System.currentTimeMillis(),
            )
        )
    }

    // ===================== 内部工具 =====================

    /** 写路径取当前账户快照（读路径请用 [accountIdFlow]，不要用这个方法） */
    private suspend fun awaitAccountId(): Long =
        currentAccountRepository.currentAccountId()

    private fun currentYearMonth(): String {
        return YearMonth.now(ZoneId.systemDefault()).toString() // "yyyy-MM"
    }

    private fun yearMonthOf(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return YearMonth.from(date).toString()
    }

    private fun ymLocalDate(ym: String): LocalDate {
        val ymObj = YearMonth.parse(ym)
        return ymObj.atDay(1)
    }

    /** 把任意毫秒时间戳归到当天 00:00（用于 pool.date / 月份区间起算） */
    private fun startOfDayMillis(epochMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    companion object {
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }
}
