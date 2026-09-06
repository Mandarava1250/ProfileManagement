package com.example.personalassistant.feature.finance.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 记账 DAO：覆盖 transactions / categories / monthly_budgets / daily_pools 四张表
 *
 * ⚠️ 零钱池相关写入必须使用 [transactionalRecordExpense] / [transactionalRecordIncome] 等事务方法，
 *    保证 transactions 与 daily_pools 的原子性（说明书 6.7 零钱池专项注意点）
 */
@Dao
interface FinanceDao {

    // ==================== 交易记录 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(tx: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeTransactionById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun observeByType(type: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type AND date BETWEEN :start AND :end ORDER BY date DESC")
    fun observeByTypeBetween(type: Int, start: Long, end: Long): Flow<List<TransactionEntity>>

    /** FTS 搜索：备注 + 分类名 */
    @Query(
        """
        SELECT t.* FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE t.note LIKE '%' || :keyword || '%'
           OR c.name LIKE '%' || :keyword || '%'
        ORDER BY t.date DESC
        """
    )
    fun searchTransactions(keyword: String): Flow<List<TransactionEntity>>

    // ==================== 分类 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY type, sortOrder")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder")
    fun observeCategoriesByType(type: Int): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countTransactionsByCategory(categoryId: Long): Int

    // ==================== 月度预算 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMonthlyBudget(budget: MonthlyBudgetEntity)

    @Query("SELECT * FROM monthly_budgets WHERE yearMonth = :yearMonth")
    suspend fun getMonthlyBudget(yearMonth: String): MonthlyBudgetEntity?

    @Query("SELECT * FROM monthly_budgets WHERE yearMonth = :yearMonth")
    fun observeMonthlyBudget(yearMonth: String): Flow<MonthlyBudgetEntity?>

    @Query("SELECT * FROM monthly_budgets ORDER BY yearMonth DESC")
    fun observeAllBudgets(): Flow<List<MonthlyBudgetEntity>>

    // ==================== 每日零钱池 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyPool(pool: DailyPoolEntity)

    @Query("SELECT * FROM daily_pools WHERE date = :date")
    suspend fun getDailyPool(date: Long): DailyPoolEntity?

    @Query("SELECT * FROM daily_pools WHERE date = :date")
    fun observeDailyPool(date: Long): Flow<DailyPoolEntity?>

    @Query("SELECT * FROM daily_pools ORDER BY date DESC LIMIT :limit")
    fun observeRecentPools(limit: Int): Flow<List<DailyPoolEntity>>

    /** 原子扣减：避免并发记账时出现竞态条件 */
    @Query(
        """
        UPDATE daily_pools
        SET remainingAmount = remainingAmount - :amount,
            totalSpentToday = totalSpentToday + :amount,
            lastUpdated = :now
        WHERE date = :date
        """
    )
    suspend fun deductFromPool(date: Long, amount: Double, now: Long)

    /** 原子增加（收入补入零钱池） */
    @Query(
        """
        UPDATE daily_pools
        SET remainingAmount = remainingAmount + :amount,
            totalIncomeToday = totalIncomeToday + :amount,
            lastUpdated = :now
        WHERE date = :date
        """
    )
    suspend fun addIncomeToPool(date: Long, amount: Double, now: Long)

    // ==================== 统计查询 ====================

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 0 AND date >= :monthStart AND date < :monthEnd")
    suspend fun getMonthlyExpense(monthStart: Long, monthEnd: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 1 AND date >= :monthStart AND date < :monthEnd")
    suspend fun getMonthlyIncome(monthStart: Long, monthEnd: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 0 AND date >= :monthStart AND date < :monthEnd")
    fun observeMonthlyExpense(monthStart: Long, monthEnd: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 1 AND date >= :monthStart AND date < :monthEnd")
    fun observeMonthlyIncome(monthStart: Long, monthEnd: Long): Flow<Double?>

    /** 按分类汇总（饼图用） */
    @Query(
        """
        SELECT categoryId, SUM(amount) as total
        FROM transactions
        WHERE type = 0 AND date BETWEEN :start AND :end
        GROUP BY categoryId
        ORDER BY total DESC
        """
    )
    fun getExpenseByCategory(start: Long, end: Long): Flow<List<CategorySum>>

    /** 按日汇总（折线图用） */
    @Query(
        """
        SELECT date / 86400000 as dayBucket, SUM(amount) as total
        FROM transactions
        WHERE type = 0 AND date BETWEEN :start AND :end
        GROUP BY dayBucket
        ORDER BY dayBucket
        """
    )
    fun getExpenseTrend(start: Long, end: Long): Flow<List<DayBucketSum>>

    /** 按月汇总（柱状图 / 月度报表） */
    @Query(
        """
        SELECT
          CAST((date / 86400000) AS INTEGER) as monthBucket,
          SUM(CASE WHEN type = 0 THEN amount ELSE 0 END) as expense,
          SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as income
        FROM transactions
        WHERE date >= :start
        GROUP BY monthBucket
        ORDER BY monthBucket
        """
    )
    fun getMonthlySummary(start: Long): Flow<List<MonthlySummary>>

    // ==================== 事务性写入（核心：保证 transactions + daily_pools 原子） ====================

    /**
     * 记录一笔支出：插入 transactions 行 + 同步扣减当日零钱池
     * 两者在同一事务中，失败回滚（说明书 6.7）
     */
    @Transaction
    suspend fun recordExpense(
        tx: TransactionEntity,
        poolDate: Long,
        amount: Double,
        now: Long = System.currentTimeMillis()
    ): Long {
        val txId = insertTransaction(tx)
        // 若当日零钱池不存在，先创建（仅当月预算已设置时才创建，由调用方保证）
        if (getDailyPool(poolDate) == null) {
            insertDailyPool(
                DailyPoolEntity(
                    date = poolDate,
                    dailyBudget = 0.0,
                    remainingAmount = 0.0,
                    lastUpdated = now
                )
            )
        }
        deductFromPool(poolDate, amount, now)
        return txId
    }

    @Transaction
    suspend fun recordIncome(
        tx: TransactionEntity,
        poolDate: Long,
        amount: Double,
        now: Long = System.currentTimeMillis()
    ): Long {
        val txId = insertTransaction(tx)
        if (getDailyPool(poolDate) == null) {
            insertDailyPool(
                DailyPoolEntity(
                    date = poolDate,
                    dailyBudget = 0.0,
                    remainingAmount = 0.0,
                    lastUpdated = now
                )
            )
        }
        addIncomeToPool(poolDate, amount, now)
        return txId
    }

    @Transaction
    suspend fun deleteTransactionAndRollbackPool(
        tx: TransactionEntity,
        poolDate: Long,
        now: Long = System.currentTimeMillis()
    ) {
        deleteTransaction(tx)
        when (tx.type) {
            0 -> {
                // 支出回滚：remainingAmount += amount, totalSpentToday -= amount
                if (getDailyPool(poolDate) != null) {
                    deductFromPool(poolDate, -tx.amount, now) // 用 -amount 实现加法
                }
            }
            1 -> {
                // 收入回滚
                if (getDailyPool(poolDate) != null) {
                    addIncomeToPool(poolDate, -tx.amount, now)
                }
            }
        }
    }

    /**
     * 原子编辑一笔流水：删旧 + 同步回滚旧 pool；插新 + 应用新 pool。
     * 全程同一 Room 事务，失败回滚（说明书 6.7）。
     *
     * 任何编辑都走"退旧+重插"路径，便于：
     * - 类型/金额/日期/分类变更都按相同语义处理
     * - 实现简单，无需判断"是否真变化"
     *
     * 注意：调用方负责传入同 id 的 newTx（id 与 oldTx 保持一致，便于审计追溯）。
     */
    @Transaction
    suspend fun transactionalUpdateTransaction(
        oldTx: TransactionEntity,
        newTx: TransactionEntity,
        now: Long = System.currentTimeMillis()
    ) {
        // 1. 删除旧 tx 并回滚旧 pool（事务内）
        deleteTransactionAndRollbackPool(oldTx, startOfDayMillis(oldTx.date), now)
        // 2. 插入新 tx 并应用新 pool（事务内）
        val newPoolDate = startOfDayMillis(newTx.date)
        when (newTx.type) {
            0 -> recordExpense(newTx, newPoolDate, newTx.amount, now)
            1 -> recordIncome(newTx, newPoolDate, newTx.amount, now)
        }
    }

    /** 把任意毫秒时间戳归到当天 00:00（与 RepositoryImpl 内实现对齐） */
    private fun startOfDayMillis(epochMillis: Long): Long {
        val zone = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }
}

/** 按日汇总 */
data class DayBucketSum(
    val dayBucket: Long,
    val total: Double
)

/** 按月汇总 */
data class MonthlySummary(
    val monthBucket: Long,
    val expense: Double,
    val income: Double
)
