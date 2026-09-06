package com.example.personalassistant.feature.finance.domain.repository

import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Phase 7a：记账模块领域仓库接口
 *
 * 由 Data 层的 FinanceRepositoryImpl 实现。
 *
 * ## 接口语义
 * - **读流**：observeXxx 返回 Flow，UI 层直接 collect
 * - **写操作**：suspend，由 UseCase 层调用
 *
 * ## 零钱池一致性约定
 * - addTransaction / deleteTransaction 内部走 FinanceDao 的事务方法
 *   保证 transactions + daily_pools 的原子写入/回滚
 * - refreshDailyPool 是「创建/刷新当日 zero money pool」的核心入口
 *   由 WorkManager DailyPoolRefreshWorker 和冷启动 Application.onCreate 调用
 */
interface FinanceRepository {

    // ===== 流水 =====

    /**
     * 新增一条流水（支出/收入二选一）。
     *
     * - 内部走 [com.example.personalassistant.feature.finance.data.local.FinanceDao.recordExpense]
     *   或 recordIncome，在同一事务中写入 transactions + 更新 daily_pools。
     * - 返回新生成的 transaction.id。
     */
    suspend fun addTransaction(
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
    ): Long

    /**
     * 删除流水（同时回滚零钱池）。
     * - id 不存在时静默 no-op（不抛异常）
     */
    suspend fun deleteTransaction(id: Long)

    /** 全部流水，按 date DESC, createdAt DESC */
    fun observeAllTransactions(): Flow<List<Transaction>>

    /** 区间流水（按 date BETWEEN），用于"本月"/"今日"等过滤 */
    fun observeTransactionsBetween(start: Long, end: Long): Flow<List<Transaction>>

    /** 按类型 + 区间查询 */
    fun observeByTypeBetween(
        type: TransactionType,
        start: Long,
        end: Long,
    ): Flow<List<Transaction>>

    // ===== 分类 =====

    /** 全部分类（按 type, sortOrder） */
    fun observeCategories(): Flow<List<Category>>

    /** 按类型过滤的分类 */
    fun observeCategoriesByType(type: TransactionType): Flow<List<Category>>

    /**
     * 新增或更新分类（id=0 时新增；id>0 时更新）。
     *
     * 返回落库后的 category id。
     */
    suspend fun upsertCategory(category: Category): Long

    /**
     * 删除分类。
     *
     * - 若分类下还有交易记录，抛 [CategoryInUseException]
     *   （UI 上提示用户"该分类下还有 X 笔交易，无法删除"）
     * - id 不存在时静默 no-op
     */
    suspend fun deleteCategory(id: Long)

    // ===== 月度预算 =====

    /** 当月预算（yearMonth = 当月 yyyy-MM） */
    fun observeCurrentMonthBudget(): Flow<MonthlyBudget?>

    /** 全部预算，按 yearMonth DESC */
    fun observeAllBudgets(): Flow<List<MonthlyBudget>>

    /**
     * 设置/更新月度预算（yearMonth 已存在则覆盖）。
     *
     * - 返回落库后的 budget id
     * - 立即触发零钱池重算（不依赖 WorkManager）
     */
    suspend fun setMonthlyBudget(
        yearMonth: String,
        totalAmount: Double,
        isAutoRefresh: Boolean,
        carryOverEnabled: Boolean,
        warningThreshold: Double,
    ): Long

    // ===== 零钱池 =====

    /** 当日零钱池（date = 当天 00:00 时间戳） */
    fun observeTodayPool(): Flow<DailyPool?>

    /**
     * 刷新「传入日期」对应那天的零钱池（核心算法入口）。
     *
     * ## 算法（涟宝解读，参考说明书 5.3.2）
     * 1. 取当月 MonthlyBudget；若不存在，直接 no-op（不创建 pool）
     * 2. 取当月累计 expense（不含今日以前的支出）
     * 3. 计算 dailyBudget = (totalAmount - 已支出) / 当月剩余天数（含今日）
     * 4. 决定 carryOver：
     *    - carryOverEnabled = false → carryOver = 0
     *    - carryOverEnabled = true  → 取昨日 pool.remainingAmount（>= 0 才结转）
     * 5. 已存在当日 pool → no-op（幂等）
     *    不存在 → 创建今日 pool：
     *      dailyBudget = 上一步计算值
     *      remainingAmount = dailyBudget + carryOver
     *      lastUpdated = now
     *
     * @param today 任意时刻（用于计算当天 00:00 时间戳 + 当月 yearMonth）
     */
    suspend fun refreshDailyPool(today: Long)
}

/** 分类仍有交易引用，无法删除 */
class CategoryInUseException(
    val categoryId: Long,
    val transactionCount: Int,
) : IllegalStateException(
    "分类 $categoryId 还有 $transactionCount 笔交易，无法删除"
)
