package com.example.personalassistant.feature.finance.domain.repository

import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.AccountBalanceSum
import com.example.personalassistant.feature.finance.domain.model.AccountBudgetSummary
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.model.GlobalMonthlySums
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
 *
 * ## Phase 5：为什么本接口**新增**了方法（而 Phase 3 刻意一个都没改）
 * Phase 3 不改签名，是因为账户只是「读视角切换」——语义不变，实现内部带上 accountId 就够。
 * 转账不一样，它是**新的业务动作**（同时写两个账户）且汇总是**新的读需求**（跨全部账户），
 * 藏进现有方法只会让签名变成带隐藏参数的怪东西。所以这里显式加方法，
 * 代价是 [com.example.personalassistant.feature.finance.data.repository.FakeFinanceRepository]
 * 要同步实现（编译会立刻提醒，不会漏）。
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
        attachmentUri: String? = null,
    ): Long

    // ===== Phase 5：跨账户转账 =====

    /**
     * 从**当前账户**向 [toAccountId] 转 [amount] 元，同一事务写两条配对流水。
     *
     * 故意不提供「指定 from 账户」的重载：转账的转出方永远是当前账户，
     * 这是 UI 语义（「我在看 A 账本，从 A 转出去」），也是防止
     * 「在 A 的页面上把钱从 B 转走」这类越权路径的最简单办法。
     *
     * 抛出 [TransferNotAllowedException] 的情况（全部前置校验，不写任何数据）：
     * - 金额 <= 0
     * - 目标账户不存在 / 目标就是当前账户
     * - 当前账户余额不足（**允许负余额的账户除外** —— 见 [allowNegativeBalance]）
     *
     * @param allowNegativeBalance 信用卡/负债类账户本来就可以负，硬拦会让功能不可用。
     *   默认 false（余额不足即拒绝）；UI 暂不暴露该开关，预留给账户设置。
     * @return 转出侧流水 id
     */
    suspend fun transfer(
        toAccountId: Long,
        amount: Double,
        note: String?,
        date: Long,
        allowNegativeBalance: Boolean = false,
    ): Long

    /**
     * 删除一笔转账（同事务删两条配对流水）。
     *
     * 与 [deleteTransaction] 的区别：后者只删自己一行且会去回滚零钱池，
     * 用在转账上会留下**半对转账**（对方白得钱）并白跑一次 pool 更新。
     * 所以转账必须走本方法，[deleteTransaction] 遇到转账行会拒绝（见其实现注释）。
     *
     * @return 实际删除的行数（0 = 未找到；正常为 2）
     */
    suspend fun deleteTransfer(id: Long): Int

    // ===== Phase 5：跨账户汇总（**故意不带当前账户过滤**）=====

    /**
     * 全部账户的当月预算行（按账户逐行列出）。
     *
     * ⚠️ 与其余 observe* 方法的根本区别：它**不** flatMapLatest 到当前账户，
     * 因为它要的就是所有账户。新增此类方法时必须想清楚属于哪一边。
     */
    fun observeAccountBudgetSummaries(): Flow<List<AccountBudgetSummary>>

    /** 全部账户的余额分量（不含期初，期初由 UseCase 与账户表拼接） */
    fun observeBalanceSums(): Flow<List<AccountBalanceSum>>

    /** 全账户合并的当月总额（合计卡） */
    fun observeGlobalMonthlySums(): Flow<GlobalMonthlySums?>

    /**
     * 删除流水（同时回滚零钱池）。
     * - id 不存在时静默 no-op（不抛异常）
     *
     * ⚠️ Phase 5：传入转账流水会抛 [TransferNotAllowedException]。
     * 转账必须成对删（见 [deleteTransfer]），单删一半是静默错账；
     * 与其静默做错的事，不如明确拒绝。
     */
    suspend fun deleteTransaction(id: Long)

    /**
     * 编辑流水（原子：退旧 pool + 应用新 pool，同事务）。
     *
     * 涟宝解读：任何编辑都走"退旧 + 插新"路径，DAO 内部保证原子性。
     * - id 不存在时静默 no-op
     * - createdAt / source 保留原值
     * - attachmentUri：调用方需显式传入（null 表示清空现有附件）；不传则默认 null
     */
    suspend fun updateTransaction(
        id: Long,
        type: TransactionType,
        amount: Double,
        categoryId: Long,
        note: String?,
        date: Long,
        attachmentUri: String? = null,
    )

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

    /**
     * 关键字搜索流水（7b 新增）。
     * 匹配范围：备注 + 分类名（DAO 已实现 `OR note LIKE / category.name LIKE`）。
     *
     * - 关键字为空 / 空白时返回全部流水（语义同 [observeAllTransactions]）
     * - 区分大小写（SQLite 默认 NOCASE collation 不在所有设备上保证，先按大小写敏感实现）
     */
    fun searchTransactions(keyword: String): Flow<List<Transaction>>

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

/**
 * Phase 5：转账被拒绝（原因见 [message]）
 *
 * 为什么复用同一个异常类而不是抛 IllegalArgumentException：
 * UI 需要区分「用户填错了」（金额<=0 → 提示重填）和
 * 「业务规则不允许」（余额不足 / 目标是同一账户 → 提示换账户或改金额），
 * 且这两个分支的文案要能直接显示。用类型区分比解析 message 字符串可靠。
 */
class TransferNotAllowedException(
    val reason: Reason,
    message: String,
) : IllegalArgumentException(message) {

    /** 拒绝原因（顺序与校验顺序一致） */
    enum class Reason {
        /** 金额 <= 0 */
        INVALID_AMOUNT,

        /** 目标账户不存在（已删除或非法 id） */
        TARGET_NOT_FOUND,

        /** 目标就是当前账户（自己转给自己没有意义，且会造出一对无用的自指流水） */
        SELF_TRANSFER,

        /** 余额不足且该账户不允许负余额 */
        INSUFFICIENT_BALANCE,

        /** 当前账户缺少内置「转账」分类（迁移未生效或被人删了），无法满足外键 */
        TRANSFER_CATEGORY_MISSING,

        /**
         * 对转账流水调用了普通收支的删除/编辑入口。
         *
         * 与 [SELF_TRANSFER] 分开的理由：前者是「用户操作合法但选错了 API」，
         * 属于**代码 bug**（UI 应该早已把转账分流到 deleteTransfer）；
         * 后者是用户真能做错的输入。两者混在一个 reason 里，
         * 上层面就没法区分「该提 bug」和「该提示用户」。
         */
        TRANSFER_NEEDS_PAIR_DELETE,
    }
}
