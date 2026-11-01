package com.example.personalassistant.feature.finance.data.repository

import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.finance.data.local.DailyPoolEntity
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.local.MonthlyBudgetEntity
import com.example.personalassistant.feature.finance.data.local.TransactionEntity
import com.example.personalassistant.feature.finance.data.mapper.toDomain
import com.example.personalassistant.feature.finance.data.mapper.toEntity
import com.example.personalassistant.feature.finance.data.seed.DefaultCategories
import com.example.personalassistant.feature.finance.domain.BillingCycle
import com.example.personalassistant.feature.finance.domain.MonthCutoffPolicy
import com.example.personalassistant.feature.finance.domain.model.AccountBalanceSum
import com.example.personalassistant.feature.finance.domain.model.AccountBudgetSummary
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.DailyPool
import com.example.personalassistant.feature.finance.domain.model.GlobalMonthlySums
import com.example.personalassistant.feature.finance.domain.model.MonthlyBudget
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.CategoryInUseException
import com.example.personalassistant.feature.finance.domain.repository.FinanceRepository
import com.example.personalassistant.feature.finance.domain.repository.TransferNotAllowedException
import com.example.personalassistant.feature.profile.data.AccountRepository
import com.example.personalassistant.feature.profile.data.CurrentAccountRepository
import com.example.personalassistant.feature.settings.FinanceCutoffSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
 *
 * ## Phase 5：转账与跨账户汇总
 * - **转账不走 [addTransaction]**：那个方法的签名里没有账户参数（写死当前账户），
 *   而转账天生需要两个账户。把它硬塞进 enum 分支还会让记账页的类型分段按钮
 *   从 2 项变 4 项、其中 2 项非法，所以走独立的 [transfer]。
 * - [addTransaction] 里的 `when (type)` 对转账类型**显式抛错**而不是默默处理：
 *   这个 when 走的是 enum（穷尽），新增枚举值编译器会强制这里表态 ——
 *   这正是选「新增 enum 值」而不是「复用支出/收入」时指望得到的保护。
 * - 三个汇总读（预算行 / 余额分量 / 全局月度）**故意不 flatMapLatest 到当前账户**，
 *   与上面那条铁律不矛盾：它们要的语义就是「所有账户」。
 * - [observeCategories] 过滤掉内置「转账」分类：分类管理页走的是不过滤 type 的
 *   全量查询，不挡就会多出一个用户从未创建、且删掉后第一次转账就撞外键的分类行。
 *
 * ## Phase 5：依赖 [AccountRepository] 会不会成 Hilt 环
 * 不会。[AccountRepository] 的实现只依赖 UserProfileDao / FinanceDao /
 * CurrentAccountRepository / AvatarStorage，**不**依赖 FinanceRepository。
 * 方向是 FinanceRepositoryImpl → AccountRepository，单向。
 */
@Singleton
class FinanceRepositoryImpl @Inject constructor(
    private val financeDao: FinanceDao,
    private val currentAccountRepository: CurrentAccountRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: FinanceCutoffSettings,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : FinanceRepository {

    /** 当前账户 id 的读源头：订阅即建立，切账户即重发 */
    private val accountIdFlow: Flow<Long> = currentAccountRepository.currentAccountIdFlow()

    /**
     * 记账账期的月截止日策略
     *
     * 默认 1 号 = 自然月，与本设置上线前行为完全一致。
     * 用户改了之后，所有按月聚合的读路径都跟着改用新账期。
     */
    private val cutoffPolicyFlow: Flow<MonthCutoffPolicy> = combine(
        settingsRepository.financeMonthCutoffDay,
        settingsRepository.financeMonthCutoffEffectiveFrom,
    ) { day, effectiveFrom ->
        MonthCutoffPolicy(cutoffDay = day, effectiveFromYm = effectiveFrom)
    }

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
            // Phase 5：转账必须走 [transfer]（同时写两个账户的两行）。
            // 到这里只可能有一条路径：调用方绕过 UI 直接拿 TRANSFER_* 调本方法。
            // 抛错而不是「归到支出处理」：后者会静默记一笔假支出且错扣零钱池。
            TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN ->
                throw IllegalArgumentException(
                    "转账流水请走 FinanceRepository.transfer()，" +
                        "addTransaction 不处理 ${type.name}（会漏写配对行）",
                )
        }
    }

    override suspend fun deleteTransaction(id: Long) = withContext(dispatcher) {
        val accountId = awaitAccountId()
        val tx = financeDao.getTransactionById(accountId, id) ?: return@withContext
        // Phase 5 守卫：转账必须成对删，走这里会留下半对（对方白得钱）。
        // 不默默改成 deleteTransfer 而是报错：调用方明确选了哪个方法，
        // 替他换语义会让「删的到底是什么」变成猜谜。
        requireNotTransfer(tx.type, "删除")
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
        // Phase 5：转账不支持原地编辑。除了「UI 不提供入口」以外，还要在数据层拦住：
        // 否则 CSV 导入、备份恢复、日后新增的任何调用方都能触发
        // DAO 里那个 Int when 的不匹配分支，结果是旧行被删、新行不插入（转账静默消失）。
        requireNotTransfer(oldTx.type, "编辑")
        require(type != TransactionType.TRANSFER_OUT && type != TransactionType.TRANSFER_IN) {
            "不能把一笔普通流水改成转账：转账必须同时写两个账户的两行"
        }
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

    // ===================== Phase 5：跨账户转账 =====================

    /**
     * 一笔转账 = 两条配对流水。所有校验都在写入之前，写入走 DAO 的 @Transaction。
     *
     * 校验顺序（先便宜后贵，任一不过就**不写任何数据**）：
     * 金额 → 不转给自己 → 目标存在 → 双方都有转账分类 → 余额充足
     */
    override suspend fun transfer(
        toAccountId: Long,
        amount: Double,
        note: String?,
        date: Long,
        allowNegativeBalance: Boolean,
    ): Long = withContext(dispatcher) {
        val fromAccountId = awaitAccountId()

        if (amount <= 0.0) {
            throw TransferNotAllowedException(
                TransferNotAllowedException.Reason.INVALID_AMOUNT,
                "转账金额必须大于 0",
            )
        }
        if (fromAccountId == toAccountId) {
            throw TransferNotAllowedException(
                TransferNotAllowedException.Reason.SELF_TRANSFER,
                "不能向当前账户自己转账",
            )
        }
        val target = accountRepository.findAccount(toAccountId)
            ?: throw TransferNotAllowedException(
                TransferNotAllowedException.Reason.TARGET_NOT_FOUND,
                "目标账户不存在（可能已被删除）",
            )

        // 两个账户各自的转账分类都必须存在：categoryId 是 NOT NULL + 复合外键，
        // 缺任何一个都会让写入在事务里撞外键 —— 那时报的是 SQLite 错误码，
        // 用户完全看不懂。提前拦成可读文案，且**不静默补插分类行**。
        val fromCategory = requireTransferCategory(fromAccountId, "当前账户")
        val toCategory = requireTransferCategory(toAccountId, "「${target.name}」")

        if (!allowNegativeBalance) {
            val balance = currentBalance(fromAccountId)
            if (balance < amount) {
                throw TransferNotAllowedException(
                    TransferNotAllowedException.Reason.INSUFFICIENT_BALANCE,
                    "余额不足：当前 %.2f，需转 %.2f".format(balance, amount),
                )
            }
        }

        val pairId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }

        val outTx = TransactionEntity(
            id = 0L,
            accountId = fromAccountId,
            amount = amount,
            type = TransactionType.TRANSFER_OUT.storageValue,
            categoryId = fromCategory.id,
            date = date,
            note = trimmedNote,
            createdAt = now,
            source = SOURCE_TRANSFER,
            transferAccountId = toAccountId,
            transferPairId = pairId,
        )
        // 用 copy 而不是另写一个构造：两条流水除了账户/类型/分类/transferAccountId
        // 这四个字段外必须完全一致（金额、日期、备注、createdAt、pairId）。
        // 手写两遍迟早会漏一个字段，而那种不一致在 UI 上只是「两条时间差了几毫秒」，根本看不出来。
        val inTx = outTx.copy(
            id = 0L, // 必须重置：否则两条抢同一个主键，REPLACE 会把刚插入的那条顶掉
            accountId = toAccountId,
            type = TransactionType.TRANSFER_IN.storageValue,
            categoryId = toCategory.id,
            transferAccountId = fromAccountId,
        )
        financeDao.recordTransferPair(outTx, inTx)
    }

    override suspend fun deleteTransfer(id: Long): Int = withContext(dispatcher) {
        val accountId = awaitAccountId()
        financeDao.deleteTransferPair(accountId, id)
    }

    // ===================== Phase 5：跨账户汇总读 =====================
    // ⚠️ 以下三个**不**包 accountIdFlow：它们的语义就是「所有账户加起来」。
    // 若哪天给它们加了 flatMapLatest，汇总会静默变成「只剩当前账户」，
    // 而账面看起来完全正常 —— 这是本轮最容易被无意破坏的地方。

    override fun observeAccountBudgetSummaries(): Flow<List<AccountBudgetSummary>> =
        cutoffPolicyFlow.flatMapLatest { policy ->
            val zone = ZoneId.systemDefault()
            val ym = BillingCycle.yearMonthOf(LocalDate.now(zone), policy, zone)
            // 账期区间：左闭右开，与 getMonthlyExpense 用同一套口径，不拿 BETWEEN
            val (monthStart, monthEnd) = BillingCycle.rangeOf(ym, policy, zone)
            financeDao.observeAccountBudgetSummaries(ym, monthStart, monthEnd)
        }

    override fun observeBalanceSums(): Flow<List<AccountBalanceSum>> =
        financeDao.getBalanceSums()

    override fun observeGlobalMonthlySums(): Flow<GlobalMonthlySums?> =
        cutoffPolicyFlow.flatMapLatest { policy ->
            val zone = ZoneId.systemDefault()
            val ym = BillingCycle.yearMonthOf(LocalDate.now(zone), policy, zone)
            val (monthStart, monthEnd) = BillingCycle.rangeOf(ym, policy, zone)
            financeDao.getGlobalMonthlySums(monthStart, monthEnd)
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
        }.map { list ->
            // 过滤内置「转账」分类（type=2）。分类管理页走的是本方法（不过滤 type 的全量查询），
            // 不挡就会多出一个用户从未创建、也看不懂的分类，而且它能被删掉 ——
            // 删完第一次转账就撞外键。
            // 按 storageValue 原始值判而不走 toDomain：toDomain 会把 2 映射成 TRANSFER_OUT，
            // 用领域值判会漏。且不过度过滤（不用「只留 0/1」的白名单），
            // 以免把用户自己建的其它分类一并误杀。
            list.filter { it.type != DefaultCategories.CATEGORY_TYPE_TRANSFER }
                .map { it.toDomain() }
        }

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
        combine(accountIdFlow, cutoffPolicyFlow) { accountId, policy -> accountId to policy }
            .flatMapLatest { (accountId, policy) ->
                val zone = ZoneId.systemDefault()
                val ym = BillingCycle.yearMonthOf(LocalDate.now(zone), policy, zone)
                financeDao.observeMonthlyBudget(accountId, ym)
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
        warningThreshold: Double?,
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
            // null = 未设置，落 0.0；语义与"关闭警告"等价
            warningThreshold = warningThreshold ?: 0.0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        financeDao.setMonthlyBudget(entity)
        // 如果修改的是当月账期的预算，触发立即刷新（不等 Worker）
        val policy = cutoffPolicyFlow.first()
        val zone = ZoneId.systemDefault()
        val currentCycleYm = BillingCycle.yearMonthOf(LocalDate.now(zone), policy, zone)
        if (yearMonth == currentCycleYm) {
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
        val zone = ZoneId.systemDefault()
        val policy = cutoffPolicyFlow.first()
        val todayDate = Instant.ofEpochMilli(today).atZone(zone).toLocalDate()
        // 当前所在账期（标签 = 段起始所在自然月）
        val ym = BillingCycle.yearMonthOf(todayDate, policy, zone)

        // 1. 取当月账期预算；不存在 → no-op
        val budget = financeDao.getMonthlyBudget(accountId, ym) ?: return@withContext

        // 2. 取当月账期已支出（含今日）
        val (monthStart, monthEndExclusive) = BillingCycle.rangeOf(ym, policy, zone)

        // 性能优化：下面这 3 个查询互不依赖（都只读，只依赖 accountId / 账期 / 日期），
        // 原来是 3 次串行 DB 往返，现在并发 → 墙钟时间按最慢的那次算。
        // 语义完全一致：
        // - 昨日 pool 依然只在 budget.carryOverEnabled 时才查（关掉结转 = 不查）
        // - 「今日 pool 已存在 → 幂等跳过」的判断用的是同一个值，只是提前读了
        val (monthExpense, yesterdayPool, todayPool) = coroutineScope {
            val expense = async {
                financeDao.getMonthlyExpense(
                    accountId = accountId,
                    monthStart = monthStart,
                    monthEnd = monthEndExclusive,
                ) ?: 0.0
            }
            val yesterday = async {
                if (!budget.carryOverEnabled) {
                    null
                } else {
                    val yesterdayPoolDate = todayPoolDate - ONE_DAY_MS
                    financeDao.getDailyPool(accountId, yesterdayPoolDate)
                }
            }
            val todayExisting = async {
                financeDao.getDailyPool(accountId, todayPoolDate)
            }
            Triple(expense.await(), yesterday.await(), todayExisting.await())
        }

        // 3. dailyBudget = (totalAmount - 已支出) / 账期剩余天数（含今日，最少 1 天）
        val remainingDays = BillingCycle.remainingDays(todayDate, policy, zone)
        val remainingAmount = (budget.totalAmount - monthExpense).coerceAtLeast(0.0)
        val dailyBudget = remainingAmount / remainingDays

        // 4. carryOver：取昨日 pool.remainingAmount（>=0 才结转）
        //    （yesterdayPool 在 carryOverEnabled=false 时为 null → 0.0，与原来一致）
        val carryOver = yesterdayPool?.remainingAmount?.coerceAtLeast(0.0) ?: 0.0

        // 5. 当日 pool 已存在 → 幂等跳过
        if (todayPool != null) return@withContext

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

    /**
     * 当前余额 = 期初 + 流水侧净额。
     *
     * 单独抽出来是因为 [transfer] 的余额校验和 UI 显示必须用**同一个公式**，
     * 否则会出现「界面显示够用，点确认却说余额不足」这种对不上账的提示。
     */
    private suspend fun currentBalance(accountId: Long): Double {
        val initial = accountRepository.findAccount(accountId)?.initialBalance ?: 0.0
        val flowNet = financeDao.getBalanceFlowNet(accountId) ?: 0.0
        return initial + flowNet
    }

    /** 取该账户的内置转账分类；缺失即拒转（错误文案带上是哪个账户） */
    private suspend fun requireTransferCategory(
        accountId: Long,
        accountLabel: String,
    ) = financeDao.getTransferCategory(
        accountId = accountId,
        transferCategoryType = DefaultCategories.CATEGORY_TYPE_TRANSFER,
    ) ?: throw TransferNotAllowedException(
        TransferNotAllowedException.Reason.TRANSFER_CATEGORY_MISSING,
        "$accountLabel 缺少内置「转账」分类，无法记账",
    )

    /**
     * 拦住「对转账流水走普通收支的删除/编辑」。
     *
     * 为什么用抛错而不是静默改走配对删除：调用方（含未来的 CSV 导入、备份恢复）
     * 明确选了这个方法，替他换语义会让「实际删了什么」变成需要读实现才能知道的秘密。
     */
    private fun requireNotTransfer(storageType: Int, action: String) {
        if (TransactionType.fromStorageValue(storageType).isTransfer) {
            throw TransferNotAllowedException(
                TransferNotAllowedException.Reason.TRANSFER_NEEDS_PAIR_DELETE,
                "转账流水不能直接$action：请使用转账专用的成对操作，否则会留下半对转账",
            )
        }
    }

    /**
     * 当前账期标签
     *
     * 账期由 [BillingCycle] 统一计算（受「月截止日」设置影响），
     * 这里的 [policy] 决定用哪个截止日。
     */
    private suspend fun currentCycleYearMonth(
        policy: MonthCutoffPolicy,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = BillingCycle.yearMonthOf(LocalDate.now(zone), policy, zone)

    /** 把任意毫秒时间戳归到当天 00:00（用于 pool.date / 月份区间起算） */
    private fun startOfDayMillis(epochMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    companion object {
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

        /** 转账流水的 source 值（与「手动」「零钱池扣除」同一命名空间） */
        private const val SOURCE_TRANSFER = "转账"
    }
}
