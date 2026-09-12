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
 * ⚠️ 零钱池相关写入必须使用 [transactionalUpdateTransaction] / [recordExpense] / [recordIncome]
 *    等事务方法，保证 transactions 与 daily_pools 的原子性（说明书 6.7 零钱池专项注意点）
 *
 * ## Phase 3：账户隔离铁律
 * 每条**面向 UI 的** SELECT / UPDATE / COUNT 都带 `accountId = :accountId`。
 * 例外白名单只有 4 条备份快照方法（[getAllTransactions] / [getAllCategories] /
 * [getAllMonthlyBudgets] / [getAllDailyPools]）与 4 条按 id 的备份导入方法，
 * 它们必须跨账户全量，语义见各自注释。
 *
 * 漏掉一条的后果不是崩溃而是**静默串账**：A 账户的支出会吃掉 B 账户的日预算。
 * 事务方法（[recordExpense] 等）不需要额外 accountId 参数 —— 一律从传入 entity 的
 * `accountId` 派生，这样 Repository 只要保证写路径 `copy(accountId = 当前账户)` 就全线一致。
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

    /** 备份导入专用：按全局唯一 id 删，跨账户 */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    /** 带 accountId 守卫：别的账户的流水 id 也取不到，防跨账户改删 */
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND id = :id")
    suspend fun getTransactionById(accountId: Long, id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND id = :id")
    fun observeTransactionById(accountId: Long, id: Long): Flow<TransactionEntity?>

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId
        ORDER BY date DESC, createdAt DESC
        """
    )
    fun observeAllTransactions(accountId: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId AND date BETWEEN :start AND :end
        ORDER BY date DESC
        """
    )
    fun observeBetween(accountId: Long, start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId AND type = :type
        ORDER BY date DESC
        """
    )
    fun observeByType(accountId: Long, type: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE accountId = :accountId AND type = :type AND date BETWEEN :start AND :end
        ORDER BY date DESC
        """
    )
    fun observeByTypeBetween(
        accountId: Long,
        type: Int,
        start: Long,
        end: Long,
    ): Flow<List<TransactionEntity>>

    /**
     * 关键字搜索：备注 + 分类名。
     * Phase 3：JOIN 补 `t.accountId = c.accountId` —— 分类每账户一套，
     * 不加就会命中别的账户里同名分类的流水。
     */
    @Query(
        """
        SELECT t.* FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id AND t.accountId = c.accountId
        WHERE t.accountId = :accountId
           AND (t.note LIKE '%' || :keyword || '%'
             OR c.name LIKE '%' || :keyword || '%')
        ORDER BY t.date DESC
        """
    )
    fun searchTransactions(accountId: Long, keyword: String): Flow<List<TransactionEntity>>

    // ==================== 分类 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    /** 带 accountId 守卫：id 在不同账户间会重复，只按 id 查会拿错账户的分类 */
    @Query("SELECT * FROM categories WHERE accountId = :accountId AND id = :id")
    suspend fun getCategoryById(accountId: Long, id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE accountId = :accountId ORDER BY type, sortOrder")
    fun observeAllCategories(accountId: Long): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM categories
        WHERE accountId = :accountId AND type = :type
        ORDER BY sortOrder
        """
    )
    fun observeCategoriesByType(accountId: Long, type: Int): Flow<List<CategoryEntity>>

    /** 引用计数按账户统计：别的账户引用同一 categoryId 不应挡住本账户删分类 */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE accountId = :accountId AND categoryId = :categoryId
        """
    )
    suspend fun countTransactionsByCategory(accountId: Long, categoryId: Long): Int

    // ==================== 月度预算 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMonthlyBudget(budget: MonthlyBudgetEntity)

    @Query(
        """
        SELECT * FROM monthly_budgets
        WHERE accountId = :accountId AND yearMonth = :yearMonth
        """
    )
    suspend fun getMonthlyBudget(accountId: Long, yearMonth: String): MonthlyBudgetEntity?

    @Query(
        """
        SELECT * FROM monthly_budgets
        WHERE accountId = :accountId AND yearMonth = :yearMonth
        """
    )
    fun observeMonthlyBudget(accountId: Long, yearMonth: String): Flow<MonthlyBudgetEntity?>

    @Query(
        """
        SELECT * FROM monthly_budgets
        WHERE accountId = :accountId
        ORDER BY yearMonth DESC
        """
    )
    fun observeAllBudgets(accountId: Long): Flow<List<MonthlyBudgetEntity>>

    // ==================== 每日零钱池 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyPool(pool: DailyPoolEntity)

    @Query("SELECT * FROM daily_pools WHERE accountId = :accountId AND date = :date")
    suspend fun getDailyPool(accountId: Long, date: Long): DailyPoolEntity?

    @Query("SELECT * FROM daily_pools WHERE accountId = :accountId AND date = :date")
    fun observeDailyPool(accountId: Long, date: Long): Flow<DailyPoolEntity?>

    @Query(
        """
        SELECT * FROM daily_pools
        WHERE accountId = :accountId
        ORDER BY date DESC LIMIT :limit
        """
    )
    fun observeRecentPools(accountId: Long, limit: Int): Flow<List<DailyPoolEntity>>

    /**
     * 原子扣减：避免并发记账时出现竞态条件。
     * ⚠️ 必须带 accountId —— 否则 A 账户记一笔会扣掉 B 账户当天的预算。
     */
    @Query(
        """
        UPDATE daily_pools
        SET remainingAmount = remainingAmount - :amount,
            totalSpentToday = totalSpentToday + :amount,
            lastUpdated = :now
        WHERE accountId = :accountId AND date = :date
        """
    )
    suspend fun deductFromPool(accountId: Long, date: Long, amount: Double, now: Long)

    /** 原子增加（收入补入零钱池）；同样必须带 accountId */
    @Query(
        """
        UPDATE daily_pools
        SET remainingAmount = remainingAmount + :amount,
            totalIncomeToday = totalIncomeToday + :amount,
            lastUpdated = :now
        WHERE accountId = :accountId AND date = :date
        """
    )
    suspend fun addIncomeToPool(accountId: Long, date: Long, amount: Double, now: Long)

    // ==================== Phase 8 Sub-step 4：备份导出快照 ====================
    // ⚠️ 以下 4 条是「跨账户全量」白名单：备份语义 = 导出所有账户的所有数据，
    //    所以**故意不带** accountId 过滤。新增快照方法时不要照抄上面的写法。

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM monthly_budgets")
    suspend fun getAllMonthlyBudgets(): List<MonthlyBudgetEntity>

    @Query("SELECT * FROM daily_pools")
    suspend fun getAllDailyPools(): List<DailyPoolEntity>

    // ===== Phase 8 Sub-step 4：按 id 删除（导入时清理旧数据，跨账户整表清空）=====

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)

    @Query("DELETE FROM monthly_budgets WHERE id = :id")
    suspend fun deleteMonthlyBudgetById(id: Long)

    @Query("DELETE FROM daily_pools WHERE id = :id")
    suspend fun deleteDailyPoolById(id: Long)

    // ==================== Phase 3：删账户用的整账户清理 ====================
    // 仅由 AccountRepository 在「确认删除账户」时使用；4 条必须带 accountId，
    // 否则会把别的账户数据一起清掉。

    /** 该账户是否有流水（UI 删账户前置判断） */
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countTransactionsByAccount(accountId: Long): Int

    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteTransactionsByAccount(accountId: Long): Int

    @Query("DELETE FROM categories WHERE accountId = :accountId")
    suspend fun deleteCategoriesByAccount(accountId: Long): Int

    @Query("DELETE FROM monthly_budgets WHERE accountId = :accountId")
    suspend fun deleteBudgetsByAccount(accountId: Long): Int

    @Query("DELETE FROM daily_pools WHERE accountId = :accountId")
    suspend fun deleteDailyPoolsByAccount(accountId: Long): Int

    // ==================== 统计查询 ====================

    @Query(
        """
        SELECT SUM(amount) FROM transactions
        WHERE accountId = :accountId AND type = 0 AND date >= :monthStart AND date < :monthEnd
        """
    )
    suspend fun getMonthlyExpense(accountId: Long, monthStart: Long, monthEnd: Long): Double?

    @Query(
        """
        SELECT SUM(amount) FROM transactions
        WHERE accountId = :accountId AND type = 1 AND date >= :monthStart AND date < :monthEnd
        """
    )
    suspend fun getMonthlyIncome(accountId: Long, monthStart: Long, monthEnd: Long): Double?

    @Query(
        """
        SELECT SUM(amount) FROM transactions
        WHERE accountId = :accountId AND type = 0 AND date >= :monthStart AND date < :monthEnd
        """
    )
    fun observeMonthlyExpense(
        accountId: Long,
        monthStart: Long,
        monthEnd: Long,
    ): Flow<Double?>

    @Query(
        """
        SELECT SUM(amount) FROM transactions
        WHERE accountId = :accountId AND type = 1 AND date >= :monthStart AND date < :monthEnd
        """
    )
    fun observeMonthlyIncome(
        accountId: Long,
        monthStart: Long,
        monthEnd: Long,
    ): Flow<Double?>

    /** 按分类汇总（饼图用） */
    @Query(
        """
        SELECT categoryId, SUM(amount) as total
        FROM transactions
        WHERE accountId = :accountId AND type = 0 AND date BETWEEN :start AND :end
        GROUP BY categoryId
        ORDER BY total DESC
        """
    )
    fun getExpenseByCategory(
        accountId: Long,
        start: Long,
        end: Long,
    ): Flow<List<CategorySum>>

    /** 按日汇总（折线图用） */
    @Query(
        """
        SELECT date / 86400000 as dayBucket, SUM(amount) as total
        FROM transactions
        WHERE accountId = :accountId AND type = 0 AND date BETWEEN :start AND :end
        GROUP BY dayBucket
        ORDER BY dayBucket
        """
    )
    fun getExpenseTrend(accountId: Long, start: Long, end: Long): Flow<List<DayBucketSum>>

    /** 按月汇总（柱状图 / 月度报表） */
    @Query(
        """
        SELECT
          CAST((date / 86400000) AS INTEGER) as monthBucket,
          SUM(CASE WHEN type = 0 THEN amount ELSE 0 END) as expense,
          SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as income
        FROM transactions
        WHERE accountId = :accountId AND date >= :start
        GROUP BY monthBucket
        ORDER BY monthBucket
        """
    )
    fun getMonthlySummary(accountId: Long, start: Long): Flow<List<MonthlySummary>>

    // ==================== 事务性写入（核心：保证 transactions + daily_pools 原子） ====================

    /**
     * 记录一笔支出：插入 transactions 行 + 同步扣减当日零钱池
     * 两者在同一事务中，失败回滚（说明书 6.7）
     *
     * Phase 3：accountId 一律从 [tx] 派生，pool 缺失时新建也带同一 accountId，
     * 保证「流水所属账户」与「被扣减的零钱池所属账户」永不背离。
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
        if (getDailyPool(tx.accountId, poolDate) == null) {
            insertDailyPool(
                DailyPoolEntity(
                    accountId = tx.accountId,
                    date = poolDate,
                    dailyBudget = 0.0,
                    remainingAmount = 0.0,
                    lastUpdated = now
                )
            )
        }
        deductFromPool(tx.accountId, poolDate, amount, now)
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
        if (getDailyPool(tx.accountId, poolDate) == null) {
            insertDailyPool(
                DailyPoolEntity(
                    accountId = tx.accountId,
                    date = poolDate,
                    dailyBudget = 0.0,
                    remainingAmount = 0.0,
                    lastUpdated = now
                )
            )
        }
        addIncomeToPool(tx.accountId, poolDate, amount, now)
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
                if (getDailyPool(tx.accountId, poolDate) != null) {
                    deductFromPool(tx.accountId, poolDate, -tx.amount, now) // 用 -amount 实现加法
                }
            }
            1 -> {
                // 收入回滚
                if (getDailyPool(tx.accountId, poolDate) != null) {
                    addIncomeToPool(tx.accountId, poolDate, -tx.amount, now)
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
     * Phase 3：oldTx / newTx 的 accountId 必须同为当前账户（Repository 保证），
     * 旧 pool 按 oldTx.accountId 回滚、新 pool 按 newTx.accountId 应用。
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
