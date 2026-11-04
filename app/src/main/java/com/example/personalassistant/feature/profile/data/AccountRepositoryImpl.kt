package com.example.personalassistant.feature.profile.data

import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.feature.finance.data.local.FinanceDao
import com.example.personalassistant.feature.finance.data.seed.DefaultCategories
import com.example.personalassistant.feature.profile.data.local.AccountIds
import com.example.personalassistant.feature.profile.data.local.UserProfileDao
import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3：账户仓库实现
 *
 * 依赖两侧：
 * - [UserProfileDao]：账户本身
 * - [FinanceDao]：删账户时级联清记账数据 + 统计流水数
 * - [CurrentAccountRepository]：删掉当前账户后要回退到哪个账户
 * - [AvatarStorage]：Phase 4 换/删头像时清理旧文件
 *
 * 新建 / 删除都是「多表写」，走 [dispatcher]（IO）串行执行；
 * 单条 DELETE 语句自身原子，跨表的级联删除这里不包显式事务 ——
 * 中途失败会残留部分已删的记账数据但账户行还在，UI 重试即可收敛（删除是幂等操作）。
 * 若要严格原子，后续可以把级联删除提到 FinanceDao 的 @Transaction 方法里。
 */
@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val financeDao: FinanceDao,
    private val currentAccountRepository: CurrentAccountRepository,
    private val avatarStorage: AvatarStorage,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : AccountRepository {

    override fun observeAccounts(): Flow<List<UserProfileEntity>> =
        userProfileDao.observeAll()

    override suspend fun createAccount(name: String): Long = withContext(dispatcher) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "account name must not be blank" }

        val newId = userProfileDao.insert(
            UserProfileEntity(
                id = 0L, // autoGenerate
                name = trimmed,
            )
        )

        // 新账户要有一套自己的分类，否则记账页分类选择器是空的
        DefaultCategories.build(accountId = newId).forEach { entity ->
            financeDao.insertCategory(entity)
        }
        newId
    }

    override suspend fun renameAccount(accountId: Long, name: String): Unit = withContext(dispatcher) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "account name must not be blank" }
        userProfileDao.rename(accountId, trimmed)
    }

    override suspend fun transactionCount(accountId: Long): Int = withContext(dispatcher) {
        // Phase 5：用**含转账配对行**的计数。该方法的唯一生产调用点是删账户的二次确认弹窗
        // （SettingsViewModel → AccountSettingsCard「该账户下有 N 笔流水」），
        // 而下面的 deleteAccount 会连带删掉其他账户指向本账户的配对行。
        // 两边必须同一口径，否则弹窗说「删 3 笔」、实际动 5 行，
        // 另一个账户的历史被静默改了 —— 正是本轮要消除的那类错账。
        financeDao.countTransactionsAndTransferPartners(accountId)
    }

    override suspend fun exists(accountId: Long): Boolean = withContext(dispatcher) {
        userProfileDao.getById(accountId) != null
    }

    override suspend fun findAccount(accountId: Long): UserProfileEntity? =
        withContext(dispatcher) { userProfileDao.getById(accountId) }

    override suspend fun deleteAccount(accountId: Long): Unit = withContext(dispatcher) {
        // 守卫 1：默认账户是旧数据的落点，保留它可避免「所有账户都被删光」的极端状态
        if (accountId == AccountIds.DEFAULT_ACCOUNT_ID) {
            throw LastAccountException(accountId)
        }
        // 守卫 2：只剩一个账户不许删
        if (userProfileDao.count() <= 1) {
            throw LastAccountException(accountId)
        }

        // 先回退当前账户，避免 DataStore 留下指向已删账户的悬空 id
        if (currentAccountRepository.currentAccountId() == accountId) {
            val fallback = userProfileDao.getAll()
                .firstOrNull { it.id != accountId }
                ?.id ?: AccountIds.DEFAULT_ACCOUNT_ID
            currentAccountRepository.setCurrentAccountId(fallback)
        }

        // 级联清除记账域数据（顺序无关：无 FK 约束指向 user_profiles）
        // Phase 5：转账行要**两边一起清**——除了本账户自己的，还有
        // 其他账户指向本账户的配对行（transferAccountId = 本账户）。
        // 不带上它们会留下悬空转账：那笔钱永久减在对方账上、显示成
        // 「转出至（查无此人）」，而且转账禁止原地编辑 → 没有任何恢复路径。
        financeDao.deleteTransactionsAndTransferPartners(accountId)
        financeDao.deleteCategoriesByAccount(accountId)
        financeDao.deleteBudgetsByAccount(accountId)
        financeDao.deleteDailyPoolsByAccount(accountId)
        // 批次 C：零钱池。coin_pools.accountId 上有指向 user_profiles 的真外键
        // （ON DELETE CASCADE），删账户行时会自动清 —— 但**不依赖它**：
        // 外键检查开关在导入/重置等路径上会被临时关掉，那条路径下 CASCADE 不触发，
        // 零钱池就会变成孤儿行（accountId 指向已不存在的账户，UI 永远看不到也删不掉）。
        // 这里显式删一次，与上面 4 张表的写法保持一致。
        financeDao.deleteCoinPoolsByAccount(accountId)

        // Phase 4：账户头像文件也要跟着清，否则删完账户会在 filesDir/avatars 里
        // 留下永久孤儿（没有任何行引用它，UI 也再看不到，无法通过任何入口删掉）。
        // 放在删行**之前**取 avatarUri：删完行就查不到这个值了。
        userProfileDao.getById(accountId)?.avatarUri?.let { avatarStorage.delete(it) }

        userProfileDao.deleteById(accountId)
    }

    // ===== Phase 4：头像 =====

    /**
     * 只写 `avatarUri` 一列（单条 UPDATE），文件的保存与清理在
     * [SetAccountAvatarUseCase] 里编排（理由见接口注释：可测性）。
     */
    override suspend fun setAvatarUri(accountId: Long, avatarUri: String?): Unit =
        withContext(dispatcher) {
            userProfileDao.updateAvatar(accountId, avatarUri)
        }

    // ===== Phase 5：期初余额 =====

    /**
     * 只写 initialBalance 一列（单条 UPDATE），不拼整个实体回去。
     * 参数守卫：NaN / Infinity 写进 REAL 列会变成无法比较的脏数据（余额与转账校验全部靠它），
     * 所以在入口就拒掉；isFinite() 一次排除两者。
     */
    override suspend fun setInitialBalance(accountId: Long, balance: Double): Unit =
        withContext(dispatcher) {
            require(balance.isFinite()) { "期初余额必须是有限数字，实际为 $balance" }
            userProfileDao.updateInitialBalance(accountId, balance)
        }
}
