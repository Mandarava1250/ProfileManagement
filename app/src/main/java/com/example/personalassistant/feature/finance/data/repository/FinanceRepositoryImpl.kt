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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
 */
@Singleton
class FinanceRepositoryImpl @Inject constructor(
    private val financeDao: FinanceDao,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : FinanceRepository {

    // ===================== 流水 =====================

    override suspend fun addTransaction(
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
    ): Long = withContext(dispatcher) {
        val poolDate = startOfDayMillis(date)
        val txEntity = TransactionEntity(
            id = 0L,
            amount = amount,
            type = type.storageValue,
            categoryId = categoryId,
            date = date,
            note = note,
            createdAt = System.currentTimeMillis(),
            source = "手动",
            attachmentUri = null,
        )
        when (type) {
            TransactionType.EXPENSE ->
                financeDao.recordExpense(txEntity, poolDate, amount)
            TransactionType.INCOME ->
                financeDao.recordIncome(txEntity, poolDate, amount)
        }
    }

    override suspend fun deleteTransaction(id: Long) = withContext(dispatcher) {
        val tx = financeDao.getTransactionById(id) ?: return@withContext
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
    ): Unit = withContext(dispatcher) {
        // 1. 取旧 tx；不存在 → no-op
        val oldTx = financeDao.getTransactionById(id) ?: return@withContext
        // 2. 构造新 tx（保留 createdAt / source / attachmentUri，便于审计）
        val newTx = oldTx.copy(
            amount = amount,
            type = type.storageValue,
            categoryId = categoryId,
            date = date,
            note = note,
        )
        // 3. 走 DAO 事务方法：退旧 pool + 应用新 pool
        financeDao.transactionalUpdateTransaction(oldTx, newTx)
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> =
        financeDao.observeAllTransactions().map { list -> list.map { it.toDomain() } }

    override fun observeTransactionsBetween(start: Long, end: Long): Flow<List<Transaction>> =
        financeDao.observeBetween(start, end).map { list -> list.map { it.toDomain() } }

    override fun observeByTypeBetween(
        type: TransactionType,
        start: Long,
        end: Long,
    ): Flow<List<Transaction>> =
        financeDao.observeByTypeBetween(type.storageValue, start, end)
            .map { list -> list.map { it.toDomain() } }

    override fun searchTransactions(keyword: String): Flow<List<Transaction>> =
        financeDao.searchTransactions(keyword).map { list -> list.map { it.toDomain() } }

    // ===================== 分类 =====================

    override fun observeCategories(): Flow<List<Category>> =
        financeDao.observeAllCategories().map { list -> list.map { it.toDomain() } }

    override fun observeCategoriesByType(type: TransactionType): Flow<List<Category>> =
        financeDao.observeCategoriesByType(type.storageValue)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun upsertCategory(category: Category): Long = withContext(dispatcher) {
        financeDao.insertCategory(category.toEntity())
    }

    override suspend fun deleteCategory(id: Long): Unit = withContext(dispatcher) {
        // 1. 检查引用数
        val count = financeDao.countTransactionsByCategory(id)
        if (count > 0) {
            throw CategoryInUseException(categoryId = id, transactionCount = count)
        }
        // 2. 删除
        val entity = financeDao.getCategoryById(id) ?: return@withContext
        financeDao.deleteCategory(entity)
    }

    // ===================== 月度预算 =====================

    override fun observeCurrentMonthBudget(): Flow<MonthlyBudget?> {
        val ym = currentYearMonth()
        return financeDao.observeMonthlyBudget(ym).map { it?.toDomain() }
    }

    override fun observeAllBudgets(): Flow<List<MonthlyBudget>> =
        financeDao.observeAllBudgets().map { list -> list.map { it.toDomain() } }

    override suspend fun setMonthlyBudget(
        yearMonth: String,
        totalAmount: Double,
        isAutoRefresh: Boolean,
        carryOverEnabled: Boolean,
        warningThreshold: Double,
    ): Long = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val existing = financeDao.getMonthlyBudget(yearMonth)
        val entity = MonthlyBudgetEntity(
            id = existing?.id ?: 0L,
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

    override fun observeTodayPool(): Flow<DailyPool?> {
        val today = startOfDayMillis(System.currentTimeMillis())
        return financeDao.observeDailyPool(today).map { it?.toDomain() }
    }

    override suspend fun refreshDailyPool(today: Long) = withContext(dispatcher) {
        val todayPoolDate = startOfDayMillis(today)
        val ym = yearMonthOf(today)
        val zone = ZoneId.systemDefault()

        // 1. 取当月预算；不存在 → no-op
        val budget = financeDao.getMonthlyBudget(ym) ?: return@withContext

        // 2. 取当月已支出（含今日）
        val monthStart = YearMonth.from(ymLocalDate(ym)).atDay(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        // monthEndExclusive = 下月 1 号 00:00
        val monthEndExclusive = YearMonth.from(ymLocalDate(ym)).plusMonths(1)
            .atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthExpense = financeDao.getMonthlyExpense(monthStart, monthEndExclusive) ?: 0.0

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
            financeDao.getDailyPool(yesterdayPoolDate)?.remainingAmount?.coerceAtLeast(0.0) ?: 0.0
        } else {
            0.0
        }

        // 5. 当日 pool 已存在 → 幂等跳过
        if (financeDao.getDailyPool(todayPoolDate) != null) return@withContext

        // 6. 创建今日 pool
        financeDao.insertDailyPool(
            DailyPoolEntity(
                id = 0L,
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
