package com.example.personalassistant.feature.profile.data

import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Phase 4：测试用 Fake [AccountRepository]
 *
 * 只需要覆盖头像链路真正会走到的方法；其余方法保留合理默认行为，
 * 一旦被本 Phase 的测试意外调用，[unexpected] 会让测试直接失败 ——
 * 这样「头像逻辑偷偷去碰记账表」这类越界会被立刻发现。
 *
 * @param accounts 初始账户清单
 * @param orderLog 与 [FakeAvatarStorage] 共享的调用序列，用于断言**跨对象**顺序
 *                 （「先写库后删旧文件」这条不变量必须两边合起来才看得出来）
 */
class FakeAccountRepository(
    accounts: List<UserProfileEntity> = listOf(DEFAULT),
    private val orderLog: MutableList<String> = mutableListOf(),
) : AccountRepository {

    val unexpected: MutableList<String> = mutableListOf()

    private val items = MutableStateFlow(accounts)

    /** 写库调用记录：形如 `setAvatar(1 -> file://...)` */
    val avatarWrites: List<String>
        get() = orderLog.filter { it.startsWith("setAvatar(") }

    override fun observeAccounts(): Flow<List<UserProfileEntity>> = items

    override suspend fun findAccount(accountId: Long): UserProfileEntity? =
        items.value.firstOrNull { it.id == accountId }

    override suspend fun exists(accountId: Long): Boolean =
        items.value.any { it.id == accountId }

    /**
     * 写头像列。这里**直接改内存里的实体**，好让后续 findAccount 读到新值 ——
     * 否则测不了「连续换两次头像时，第二次删的应是第一次写入的那个文件」。
     */
    override suspend fun setAvatarUri(accountId: Long, avatarUri: String?) {
        orderLog.add("setAvatar($accountId -> $avatarUri)")
        items.value = items.value.map {
            if (it.id == accountId) it.copy(avatarUri = avatarUri) else it
        }
    }

    // ===== 本 Phase 不该用到的方法：记下来，断言没人调用 =====

    override suspend fun createAccount(name: String): Long {
        unexpected.add("createAccount($name)"); return 0L
    }

    override suspend fun renameAccount(accountId: Long, name: String) {
        unexpected.add("renameAccount($accountId, $name)")
    }

    override suspend fun transactionCount(accountId: Long): Int {
        unexpected.add("transactionCount($accountId)"); return 0
    }

    override suspend fun deleteAccount(accountId: Long) {
        unexpected.add("deleteAccount($accountId)")
    }

    companion object {
        val DEFAULT: UserProfileEntity
            get() = UserProfileEntity(id = 1L, name = "默认账户")

        /** 带旧头像的账户：换头像/清除头像两条路径的前置状态 */
        val WITH_AVATAR: UserProfileEntity
            get() = UserProfileEntity(
                id = 1L,
                name = "默认账户",
                avatarUri = "file:///data/user/0/app/files/avatars/old-uuid.jpg",
            )
    }
}
