package com.example.personalassistant.feature.finance.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.personalassistant.feature.finance.domain.model.AccountBalanceSum
import com.example.personalassistant.feature.finance.domain.model.AccountBudgetSummary
import com.example.personalassistant.feature.finance.domain.model.GlobalMonthlySums
import kotlinx.coroutines.flow.Flow

/**
 * 记账 DAO：覆盖 transactions / categories / monthly_budgets / daily_pools 四张表
 *
 * ⚠️ 零钱池相关写入必须使用 [transactionalUpdateTransaction] / [recordExpense] / [recordIncome]
 *    等事务方法，保证 transactions 与 daily_pools 的原子性（说明书 6.7 零钱池专项注意点）
 *
 * ## Phase 3：账户隔离铁律
 * 每条**面向 UI 的** SELECT / UPDATE / COUNT 都带 `accountId = :accountId`。
 * 例外白名单只有：
 * - 4 条备份快照方法（[getAllTransactions] / [getAllCategories] /
 *   [getAllMonthlyBudgets] / [getAllDailyPools]）与 4 条按 id 的备份导入方法；
 * - Phase 5 的 3 条**跨账户聚合**方法（[getBalanceSums] / [observeAccountBudgetSummaries] /
 *   [getGlobalMonthlySums]），它们的语义就是「把所有账户加起来」，
 *   带 accountId 反而错 —— 每条都有 ⚠️ 注释标注。
 * 新增跨账户方法时必须同步改这份名单，否则下一个人无法判断是有意还是漏了。
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

    /**
     * Phase 8 Sub-step 5（plan 8c.2）：[AttachmentCleanupWorker] 用——拉出全库 transactions 的 attachmentUri 全集
     *
     * 返回的是 `file://...` URI 字符串，调度附件 Worker 拿它跟磁盘目录里的文件名比对，
     * 找出差集（孤儿文件）后删除。
     *
     * ⚠️ 故意不带 accountId（汇总语义；一个文件被任一账户的流水引用就不删）。
     * 同步修改 DAO 头部的「账户隔离白名单」说明。
     */
    @Query("SELECT attachmentUri FROM transactions WHERE attachmentUri IS NOT NULL")
    suspend fun allAttachmentUris(): List<String>

    // ===== Phase 8 Sub-step 4：按 id 删除（导入时清理旧数据，跨账户整表清空）=====

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)

    @Query("DELETE FROM monthly_budgets WHERE id = :id")
    suspend fun deleteMonthlyBudgetById(id: Long)

    @Query("DELETE FROM daily_pools WHERE id = :id")
    suspend fun deleteDailyPoolById(id: Long)

    // ==================== 备份导入批量操作（Phase 8 Sub-step 4 同款口径） ====================
    //
    // 导入一个备份 = 清空 N 张表 + 灌入 N 张表。原来是「先把整表读回内存，再逐行删 / 逐行插」，
    // 10 万行流水就是 10 万次读 + 10 万次 DELETE + 10 万次 bind+execute。
    // 下面的批量接口把 IO 往返从 O(N) 压到 O(1)：一次 DELETE FROM、一次批量 INSERT。
    //
    // ⚠️ 冲突策略必须与对应的单条方法**逐字一致**（REPLACE ↔ REPLACE），
    //    否则同一份备份在「逐行」与「批量」两条路径下会得到不同的落库结果。

    /** 批量插入流水（REPLACE，与 [insertTransaction] 一致）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(txs: List<TransactionEntity>): List<Long>

    /** 批量插入分类（REPLACE，与 [insertCategory] 一致）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>): List<Long>

    /** 批量写入月度预算（REPLACE，与 [setMonthlyBudget] 一致）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMonthlyBudgets(budgets: List<MonthlyBudgetEntity>): List<Long>

    /** 批量写入每日零钱池（REPLACE，与 [insertDailyPool] 一致）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyPools(pools: List<DailyPoolEntity>): List<Long>

    /** 清空流水表（备份导入用；语义等价于「读回全表再逐行删」，但只有 1 次 IO）。 */
    @Query("DELETE FROM transactions")
    suspend fun clearTransactions(): Int

    /** 清空分类表。 */
    @Query("DELETE FROM categories")
    suspend fun clearCategories(): Int

    /** 清空月度预算表。 */
    @Query("DELETE FROM monthly_budgets")
    suspend fun clearMonthlyBudgets(): Int

    /** 清空每日零钱池表。 */
    @Query("DELETE FROM daily_pools")
    suspend fun clearDailyPools(): Int

    // ==================== Phase 3：删账户用的整账户清理 ====================
    // 仅由 AccountRepository 在「确认删除账户」时使用；4 条必须带 accountId，
    // 否则会把别的账户数据一起清掉。

    /** 该账户是否有流水（UI 删账户前置判断） */
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countTransactionsByAccount(accountId: Long): Int

    /**
     * 删账户专用：清掉本账户流水 + **别的账户指向本账户的转账配对行**（Phase 5）。
     *
     * 为什么必须带上 `OR transferAccountId = :accountId`：
     * 只删本账户那一侧会在其他账户留下悬空转账行 —— 那笔钱永久减在对方账上、
     * 显示成「转出 至 （查无此人）」，而且转账被人家禁止了原地编辑，
     * 所以这个状态**没有任何恢复路径**，只能靠删对端账户碰运气。
     * 两边一起删能保住「转账永远成对」这个不变量，也让全账户
     * transferIn / transferOut 保持平衡（[GlobalMonthlySums.transferImbalanced] 不会误报）。
     */
    @Query(
        """
        DELETE FROM transactions
        WHERE accountId = :accountId OR transferAccountId = :accountId
        """
    )
    suspend fun deleteTransactionsAndTransferPartners(accountId: Long): Int

    /**
     * 删账户二次确认的笔数：包含会被连带删掉的配对行（Phase 5）。
     *
     * 与 [countTransactionsByAccount] 分开是有意的：弹窗要跟用户说「删掉 N 笔」时，
     * N 必须是**实际会消失的行数**，不然用户会以为只删了自己的账，
     * 实际连带改了另一个账户的历史 —— 那正是本轮要消除的那类静默错账。
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE accountId = :accountId OR transferAccountId = :accountId
        """
    )
    suspend fun countTransactionsAndTransferPartners(accountId: Long): Int

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

    // ==================== Phase 5：转账与跨账户聚合 ====================
    // 以下 4 组是「故意跨账户」的聚合查询，语义就是要把所有账户加起来；
    // 它们同时受 accountId 过滤反而是 bug（汇总会只剩当前账户）。

    /**
     * 一次性取**全部账户**的余额分量（期初之外的那三项），供 ViewModel 与
     * `user_profiles.initialBalance` 相加得余额。
     *
     * 为什么用一条 GROUP BY 而不是 N 次单账户查询：账户数未知且会变，
     * N+1 查询在切账户/刷新时会放大成卡顿；一次聚合拿全。
     *
     * ⚠️ 故意不带 accountId 过滤。
     *
     * 口径：
     * - expense / income 只算 type 0/1，**转账不进收支**（与全部统计查询一致）
     * - transferNet = 转入(3) − 转出(2)，所以余额 = 期初 + income − expense + transferNet
     *
     * 返回类型直接用领域模型：data → domain 是本项目允许的**正向**依赖
     * （FinanceRepositoryImpl 本来就 import domain），所以下面三个聚合投影
     * 不再在 data 层重复定一套同名字段 —— 省一层转换，也少一个漂移面。
     */
    @Query(
        """
        SELECT
            accountId,
            SUM(CASE WHEN type = 0 THEN amount ELSE 0 END) AS expense,
            SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) AS income,
            SUM(CASE WHEN type = 3 THEN amount ELSE 0 END)
              - SUM(CASE WHEN type = 2 THEN amount ELSE 0 END) AS transferNet
        FROM transactions
        GROUP BY accountId
        """
    )
    fun getBalanceSums(): Flow<List<AccountBalanceSum>>

    /**
     * 预算跨账户汇总：每账户当月预算 + 当月支出 + 当月转入/转出，按账户聚合。
     *
     * LEFT JOIN 而非 INNER：**没设预算的账户也必须出现在结果里**（budgetAmount 为 NULL），
     * 否则「合计」会静默少加一个账户，而用户看不出任何异常。
     * UI 侧要把这种行标成「未设预算」，见 [AccountBudgetSummary.budgetMissing]。
     *
     * 子查询 tx 里三个 SUM 都带 `AND date >= :monthStart AND date < :monthEnd`：
     * 与 [getMonthlyExpense] 用同一套左闭右开区间，避免边界日重复计入。
     *
     * ⚠️ 故意不带 accountId 过滤（汇总语义）。
     */
    @Query(
        """
        SELECT
            up.`id` AS accountId,
            up.`name` AS accountName,
            mb.`totalAmount` AS budgetAmount,
            IFNULL(tx.expense, 0.0) AS expense,
            IFNULL(tx.transferIn, 0.0) AS transferIn,
            IFNULL(tx.transferOut, 0.0) AS transferOut,
            up.`initialBalance` AS initialBalance
        FROM `user_profiles` up
        LEFT JOIN `monthly_budgets` mb
            ON mb.`accountId` = up.`id` AND mb.`yearMonth` = :yearMonth
        LEFT JOIN (
            SELECT
                `accountId`,
                SUM(CASE WHEN `type` = 0 THEN `amount` ELSE 0 END) AS expense,
                SUM(CASE WHEN `type` = 3 THEN `amount` ELSE 0 END) AS transferIn,
                SUM(CASE WHEN `type` = 2 THEN `amount` ELSE 0 END) AS transferOut
            FROM `transactions`
            WHERE `date` >= :monthStart AND `date` < :monthEnd
            GROUP BY `accountId`
        ) tx ON tx.`accountId` = up.`id`
        ORDER BY up.`id` ASC
        """
    )
    fun observeAccountBudgetSummaries(
        yearMonth: String,
        monthStart: Long,
        monthEnd: Long,
    ): Flow<List<AccountBudgetSummary>>

    /**
     * 全账户合并的月度收支总额（合计卡用）。
     *
     * 与 [getMonthlySummary]（单账户按月分桶）互补：那个画图，这个报总数。
     * 转账不计入 expense/income，但**单独返回** transferIn/transferOut，
     * 让 UI 能显式告诉用户「有 N 元是账户之间挪的，不是真花了」。
     *
     * 四个 SUM 全部包 IFNULL：本查询无 GROUP BY，所以**永远返回一行**，
     * 而表为空时 SUM 的结果是 NULL。领域模型里这四个字段是非空 Double，
     * 交给 Room 读 NULL 属于「行为未定义，看生成代码怎么赌」，不能赌。
     *
     * ⚠️ 故意不带 accountId 过滤（汇总语义）。
     */
    @Query(
        """
        SELECT
            IFNULL(SUM(CASE WHEN type = 0 THEN amount ELSE 0 END), 0.0) AS expense,
            IFNULL(SUM(CASE WHEN type = 1 THEN amount ELSE 0 END), 0.0) AS income,
            IFNULL(SUM(CASE WHEN type = 2 THEN amount ELSE 0 END), 0.0) AS transferOut,
            IFNULL(SUM(CASE WHEN type = 3 THEN amount ELSE 0 END), 0.0) AS transferIn
        FROM transactions
        WHERE date >= :monthStart AND date < :monthEnd
        """
    )
    fun getGlobalMonthlySums(
        monthStart: Long,
        monthEnd: Long,
    ): Flow<GlobalMonthlySums?>

    /**
     * 转账配对：按 pairId 取另一行（删除/校验用）。
     *
     * `id != :excludeId` 是必需的：同一对有两行，不加这个条件会把自己也查出来，
     * 于是「删除时顺带删配对」会变成「同一行删两次」——第二次是 no-op，
     * 看起来一切正常，实际少删了一行，留下永久孤儿转账。
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE transferPairId = :pairId AND id != :excludeId
        """
    )
    suspend fun getTransferPartner(pairId: String, excludeId: Long): TransactionEntity?

    /** 取本账户的一行（带 accountId 守卫，别的账户改不到） */
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND id = :id")
    suspend fun getTransactionInAccount(accountId: Long, id: Long): TransactionEntity?

    /**
     * 删一笔转账 = 精确删掉这一对两行（同事务）。
     *
     * 为什么按 pairId 而不是按金额/日期找对方：见
     * [TransactionEntity.transferPairId] 注释 —— 模糊匹配会让「删 1 笔」变成「删 N 笔」。
     *
     * 两步必须在同一个 @Transaction 里：只删自己一行会留下**半对转账**，
     * 对方账户白得一笔钱，而派生余额让两边都显示错数字，且没有任何一行能提示这里断过。
     *
     * 转账不动零钱池（它既不是支出也不是收入），所以无 pool 回滚逻辑；
     * 余额是派生值，删掉流水行即自动生效。
     *
     * @return 实际删除的行数（0 = 未找到；正常应为 1 或 2）
     */
    @Transaction
    suspend fun deleteTransferPair(accountId: Long, id: Long): Int {
        val row = getTransactionInAccount(accountId, id) ?: return 0
        val pairId = row.transferPairId
        if (pairId == null) {
            // 不是转账行：走普通收支删除语义（本 DAO 不提供 pool 回滚，
            // 调用方应走 deleteTransactionAndRollbackPool），这里只守卫不删，
            // 避免「以为删了转账其实删的是普通流水」的静默错账。
            return 0
        }
        // 先按 pairId 删掉两行（含自己），一次 DELETE 覆盖，不依赖两行都在同一查询结果里
        return deleteTransactionsByPairId(pairId)
    }

    @Query("DELETE FROM transactions WHERE transferPairId = :pairId")
    suspend fun deleteTransactionsByPairId(pairId: String): Int

    /**
     * 取本账户内置的「转账」分类（Phase 5 转账流水的 categoryId 落点）。
     *
     * 为什么不写死 id 或用常量：分类 id 是 Room autoGenerate、**每账户一套**，
     * 而且用户可以在分类管理页改名（本查询不依赖 name，所以改名不影响）。
     * 靠 `type = 2` 定位是唯一稳的，因为它既不是用户可见的筛选条件，
     * 也是 seed / 迁移两边唯一共同写入的标记。
     *
     * 返回 null 表示该账户缺这条分类（旧库未迁移成功、或被人直接删了）——
     * 此时**必须拒绝转账**而不是现场补插一行：静默补数据会让备份/导入
     * 出现无法解释的分类行。拒绝文案见 TransferNotAllowedException。
     */
    @Query(
        """
        SELECT * FROM categories
        WHERE accountId = :accountId AND type = :transferCategoryType
        ORDER BY id ASC
        LIMIT 1
        """
    )
    suspend fun getTransferCategory(
        accountId: Long,
        transferCategoryType: Int,
    ): CategoryEntity?

    /** 当前余额的「流水侧」净额（期初之外），供转账余额校验用；无流水时 SUM 为 NULL */
    @Query(
        """
        SELECT
            SUM(CASE WHEN type = 1 THEN amount ELSE 0 END)
            - SUM(CASE WHEN type = 0 THEN amount ELSE 0 END)
            + SUM(CASE WHEN type = 3 THEN amount ELSE 0 END)
            - SUM(CASE WHEN type = 2 THEN amount ELSE 0 END)
        FROM transactions
        WHERE accountId = :accountId
        """
    )
    suspend fun getBalanceFlowNet(accountId: Long): Double?

    /**
     * 转账写入（Phase 5 核心事务）：两行必须一起成功。
     *
     * 半对状态是**最坏情况**：钱只从一个账户出去了，另一个账户没收到，
     * 而派生余额会让两边都显示错误数字，且没有任何一行数据能提示「这里断过」。
     * 所以 Room 的 @Transaction 在这里是正确性要求，不是性能优化。
     */
    @Transaction
    suspend fun recordTransferPair(
        outTx: TransactionEntity,
        inTx: TransactionEntity,
    ): Long {
        val outId = insertTransaction(outTx)
        insertTransaction(inTx)
        return outId
    }

    /**
     * 该账户是否存在指向**已删除账户**的转账行（删账户后的残留自检用）。
     *
     * 为什么要这个：删账户走 Repository 级联，若某条路径漏清了配对行，
     * 会留下 `transferAccountId` 指向不存在账户的转账。它不会崩、不会报错，
     * 只在 UI 上显示成「转入自 ?」——所以要有可断言的探测手段（测试用）。
     */
    @Query(
        """
        SELECT COUNT(*) FROM transactions t
        WHERE t.transferAccountId IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM user_profiles up WHERE up.id = t.transferAccountId
          )
        """
    )
    suspend fun countDanglingTransferRows(): Int

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
            // Phase 5：转账(2/3) 从未动过零钱池，所以删它也不需回滚。
            // 写成**显式分支**而不是让它静默掉到 when 外：Int 的 when 不要求穷尽，
            // 只靠「不匹配就什么都不做」的话，下一个人读到会以为漏写了分支，
            // 或误把新类型也归到这里。余额是派生值，删行即自动生效。
            2, 3 -> { /* 转账：无 pool 可回滚 */ }
            else -> { /* 脏数据（未知 type）：不动 pool，与 fromStorageValue 的兜底一致 */ }
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
        // ⚠️ Phase 5 守卫：转账流水**不得**走这条路。
        // 下面的 when 对 2/3 没有插入分支，若真进来，oldTx 已被删、newTx 不会插，
        // 结果是「编辑一笔转账」= 静默删掉这一对里的**一半**（半对转账，对方白得钱）。
        // 编译器对 Int 的 when 不会提醒这一点，所以在这里显式拒绝。
        // 产品层面的对应决定：转账不提供原地编辑，要改就删了重建（删会同时清配对）。
        require(newTx.type != 2 && newTx.type != 3) {
            "转账流水不支持原地编辑（会留下半对转账）；请删除后重新转账。id=${newTx.id}"
        }
        // 1. 删除旧 tx 并回滚旧 pool（事务内）
        deleteTransactionAndRollbackPool(oldTx, startOfDayMillis(oldTx.date), now)
        // 2. 插入新 tx 并应用新 pool（事务内）
        val newPoolDate = startOfDayMillis(newTx.date)
        when (newTx.type) {
            0 -> recordExpense(newTx, newPoolDate, newTx.amount, now)
            1 -> recordIncome(newTx, newPoolDate, newTx.amount, now)
            // 2/3 已被上面的 require 拦住；else 兜底不插入（与历史行为一致）
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
