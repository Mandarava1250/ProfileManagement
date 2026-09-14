package com.example.personalassistant.feature.profile.data

import android.net.Uri
import javax.inject.Inject

/**
 * Phase 4：账户头像写入 / 清除的**唯一编排点**
 *
 * ## 核心不变量（顺序敏感，一步都不能换）
 * > 新头像没成功落盘时，绝不写库、绝不删旧文件。
 *
 * 违反它的后果很具体：用户挑了一张无法解码的图（HEIC、已失效的云文件、
 * 被其他 App 撤走权限的 content://），若先删旧的再存新的，
 * **一次误操作就把头像永久弄没了**，而且界面上完全看不出发生了什么。
 *
 * 完整顺序：
 * 1. 查账户（拿**换之前**的 avatarUri，并顺带做存在性守卫）
 * 2. [AvatarStorage.saveAvatar] 写新文件 —— **失败即在此中断**，3/4 都不执行
 * 3. [AccountRepository.setAvatarUri] 写库（库里从此指向新文件）
 * 4. 删旧文件 —— 只在「确实换了」时删
 *
 * 3 先于 4 的理由：反过来做一旦中间失败，库里就留下指向**已删除文件**的 avatarUri
 * （永久裂图，且无法通过任何 UI 入口补救）。按现在的顺序，最坏只泄露一个
 * 无人引用的旧文件 —— 那是可回收的垃圾，不是损坏的数据。
 *
 * ## 为什么编排放 UseCase 而不是 Repository
 * 放 Repository（`AccountRepositoryImpl`）也能写，但**测不到**：那个类还依赖
 * `FinanceDao`（47 个方法）和 `UserProfileDao`（11 个方法），项目「Fake 优先、无 Mockito」，
 * 为了测一条头像链路要手写 58 个假方法 —— 那种成本的实际结局通常是「干脆不测」。
 * 本类只依赖 [AvatarStorage] + [AccountRepository] 两个接口（8 个方法），
 * 两个 Fake 就能把上面四步的每条分支都覆盖。
 *
 * 顺带一条反面教训（Phase 3 踩坑笔记第 6 条）：`GetMonthlyReportUseCase` 当初绕过
 * Repository 直连 DAO，DAO 一改造成就跟着坏。所以这里走 Repository，不碰 DAO。
 */
class SetAccountAvatarUseCase @Inject constructor(
    private val avatarStorage: AvatarStorage,
    private val accountRepository: AccountRepository,
) {
    /**
     * 用 [source]（PhotoPicker 给的原始图片）作为账户新头像。
     *
     * @return true = 已更新；false = **图片读不出来**，账户保持原样（旧头像仍在）
     * @throws IllegalArgumentException 账户不存在（UI 层已禁掉入口，这是兜底守卫）
     */
    suspend operator fun invoke(accountId: Long, source: Uri): Boolean {
        val account = requireNotNull(accountRepository.findAccount(accountId)) {
            "账户 $accountId 不存在"
        }

        // 守卫 2：新文件先落盘，拿不到 URI 就到此为止 —— 旧头像一个字节都不动
        val newUri = avatarStorage.saveAvatar(source) ?: return false

        // 用户重选同一张图：库里已指向它，若继续往下会把刚确认有效的文件删掉
        if (account.avatarUri == newUri) return true

        accountRepository.setAvatarUri(accountId, newUri)
        account.avatarUri?.let { avatarStorage.delete(it) }
        return true
    }

    /**
     * 清除头像（库里置 null + 删文件）。
     *
     * @return false = 账户不存在，不做任何写入
     */
    suspend fun clear(accountId: Long): Boolean {
        val account = accountRepository.findAccount(accountId) ?: return false
        val oldUri = account.avatarUri

        // 先置空库里再删文件：中途失败只留一个无人引用的文件，不会留裂图
        accountRepository.setAvatarUri(accountId, null)
        oldUri?.let { avatarStorage.delete(it) }
        return true
    }
}
