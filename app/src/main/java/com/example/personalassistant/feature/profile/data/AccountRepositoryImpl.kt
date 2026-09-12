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
        financeDao.countTransactionsByAccount(accountId)
    }

    override suspend fun exists(accountId: Long): Boolean = withContext(dispatcher) {
        userProfileDao.getById(accountId) != null
    }

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
        financeDao.deleteTransactionsByAccount(accountId)
        financeDao.deleteCategoriesByAccount(accountId)
        financeDao.deleteBudgetsByAccount(accountId)
        financeDao.deleteDailyPoolsByAccount(accountId)

        userProfileDao.deleteById(accountId)
    }
}
