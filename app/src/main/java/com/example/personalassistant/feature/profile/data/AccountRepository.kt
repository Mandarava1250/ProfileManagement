package com.example.personalassistant.feature.profile.data

import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 3：账户仓库（接口）
 *
 * 只管「账户本身」的增删改查 + 记账域数据归属；
 * 「当前选中哪个账户」是另一件事，见 [CurrentAccountRepository]。
 */
interface AccountRepository {

    /** 全部账户（按 id 升序，默认账户在最前） */
    fun observeAccounts(): Flow<List<UserProfileEntity>>

    /**
     * 新建账户，并给它 seed 一套内置分类（8 支出 + 1 收入）。
     *
     * 返回新账户 id。名字空白时抛 [IllegalArgumentException]（由 UI 负责提示）。
     */
    suspend fun createAccount(name: String): Long

    /** 重命名 */
    suspend fun renameAccount(accountId: Long, name: String)

    /** 该账户的流水数（UI 删账户前的二次确认文案用） */
    suspend fun transactionCount(accountId: Long): Int

    /** 账户是否存在（删/改前守卫） */
    suspend fun exists(accountId: Long): Boolean

    /**
     * 删除账户，并**级联清除该账户在记账域的 4 张表数据**。
     *
     * ⚠️ 不可恢复。调用方（UI）必须已经拿到用户明确二次确认；
     *    仓库内部仍会做两道守卫：
     *    - 只剩 1 个账户时抛 [LastAccountException]
     *    - 删的是当前账户时，先把当前账户切到剩余的第一个账户，再删数据
     *      （否则 DataStore 里会留下指向已删除账户的悬空 id）
     *
     * 级联清除是必须的：`transactions.categoryId` 现在是对 `categories(accountId, id)`
     * 的复合外键，只删 `user_profiles` 一行会留下无人认领的分类与流水。
     */
    suspend fun deleteAccount(accountId: Long)
}

/** 仅剩最后一个账户，禁止删除 */
class LastAccountException(
    val accountId: Long,
) : IllegalStateException("账户 $accountId 是最后一个账户，不能删除")
