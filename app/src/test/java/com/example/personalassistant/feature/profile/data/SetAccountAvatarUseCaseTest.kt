package com.example.personalassistant.feature.profile.data

import com.example.personalassistant.feature.profile.data.local.UserProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4：头像写入链路的顺序与失败守卫
 *
 * 全部围绕一条不变量：**新头像没成功落盘前，绝不能碰旧头像，也不能写库。**
 * 这条只能在编排层测（真实存储要 ContentResolver + Bitmap，见 [FakeAvatarStorage] 注释），
 * 所以两个依赖都用 Fake，并让它们把调用写进**同一个 list** —— 因为
 * 「先写库、后删旧文件」是跨两个对象的顺序，分开记看不出来。
 */
class SetAccountAvatarUseCaseTest {

    // ===== 守卫：失败即保持原状 =====

    @Test
    fun `新头像保存失败时不写库也不删旧头像`() = runTest {
        val log = mutableListOf<String>()
        val repo = FakeAccountRepository(listOf(WITH_AVATAR), log)
        val storage = FakeAvatarStorage(result = null, sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        val ok = useCase(1L, placeholderUri())

        assertFalse("图片读不出来时必须返回 false", ok)
        // 一条都不能多：写库和删旧文件都必须没发生
        assertEquals(listOf("save()"), log)
        assertEquals("库里头像必须还是旧文件", OLD_URI, repo.findAccount(1L)?.avatarUri)
    }

    @Test
    fun `账户不存在时连图片都不该尝试保存`() = runTest {
        val log = mutableListOf<String>()
        val repo = FakeAccountRepository(listOf(WITH_AVATAR), log)
        val storage = FakeAvatarStorage(sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        val thrown = runCatching { useCase(99L, placeholderUri()) }.exceptionOrNull()

        assertTrue("应抛 IllegalArgumentException", thrown is IllegalArgumentException)
        // 守卫在 save 之前：否则会白产生一个没人引用的孤儿文件
        assertEquals("不存在的账户不应产生任何 IO", emptyList<String>(), log)
    }

    // ===== 成功路径：顺序 =====

    @Test
    fun `换头像的完整顺序是 先存新文件 再写库 最后删旧文件`() = runTest {
        val log = mutableListOf<String>()
        val repo = FakeAccountRepository(listOf(WITH_AVATAR), log)
        val storage = FakeAvatarStorage(result = NEW_URI, sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        assertTrue(useCase(1L, placeholderUri()))

        assertEquals(
            listOf(
                "save()",
                "setAvatar(1 -> $NEW_URI)",
                "delete($OLD_URI)",
            ),
            log,
        )
        assertEquals("库里应指向新文件", NEW_URI, repo.findAccount(1L)?.avatarUri)
    }

    @Test
    fun `首次设置头像时没有任何旧文件可删`() = runTest {
        val log = mutableListOf<String>()
        val repo = FakeAccountRepository(listOf(NO_AVATAR), log)
        val storage = FakeAvatarStorage(result = NEW_URI, sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        assertTrue(useCase(1L, placeholderUri()))

        assertEquals(listOf("save()", "setAvatar(1 -> $NEW_URI)"), log)
        assertTrue("不该出现 delete 调用", log.none { it.startsWith("delete(") })
    }

    @Test
    fun `连续换两次头像时第二次删的是第一次刚写入的文件`() = runTest {
        val log = mutableListOf<String>()
        val repo = FakeAccountRepository(listOf(NO_AVATAR), log)
        // 第二次保存返回不同的 URI，才能区分「删的是哪一个」
        val storage = FakeAvatarStorage(sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        storage.result = NEW_URI
        useCase(1L, placeholderUri())
        assertEquals(listOf("save()", "setAvatar(1 -> $NEW_URI)"), log)

        val second = "file://avatars/second.jpg"
        storage.result = second
        log.clear()
        useCase(1L, placeholderUri())

        assertEquals(
            listOf("save()", "setAvatar(1 -> $second)", "delete($NEW_URI)"),
            log,
        )
    }

    @Test
    fun `重选同一张图时不得删掉刚确认有效的文件`() = runTest {
        val log = mutableListOf<String>()
        // 库里已存的正是本次要写入的 URI（同图重复选择）
        val repo = FakeAccountRepository(listOf(UserProfileEntity(1L, "默认账户", NEW_URI)), log)
        val storage = FakeAvatarStorage(result = NEW_URI, sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        assertTrue("同图重复设置仍算成功", useCase(1L, placeholderUri()))

        assertTrue("幂等短路：绝不能出现 delete", log.none { it.startsWith("delete(") })
        assertEquals(listOf("save()"), log)
        assertEquals(NEW_URI, repo.findAccount(1L)?.avatarUri)
    }

    // ===== 清除路径 =====

    @Test
    fun `清除头像时先置空库里再删文件`() = runTest {
        val log = mutableListOf<String>()
        val repo = FakeAccountRepository(listOf(WITH_AVATAR), log)
        val storage = FakeAvatarStorage(sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        assertTrue(useCase.clear(1L))

        assertEquals(
            listOf("setAvatar(1 -> null)", "delete($OLD_URI)"),
            log,
        )
        assertEquals(null, repo.findAccount(1L)?.avatarUri)
    }

    @Test
    fun `清除一个本来就没有头像的账户时不应删任何文件`() = runTest {
        val log = mutableListOf<String>()
        val repo = FakeAccountRepository(listOf(NO_AVATAR), log)
        val storage = FakeAvatarStorage(sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        assertTrue(useCase.clear(1L))

        assertEquals(listOf("setAvatar(1 -> null)"), log)
    }

    @Test
    fun `清除不存在账户的头像时返回 false 且不写库`() = runTest {
        val log = mutableListOf<String>()
        val repo = FakeAccountRepository(listOf(WITH_AVATAR), log)
        val storage = FakeAvatarStorage(sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        assertFalse(useCase.clear(99L))
        assertEquals(emptyList<String>(), log)
        assertEquals("旧头像必须还在", OLD_URI, repo.findAccount(1L)?.avatarUri)
    }

    // ===== 边界：不越界碰记账逻辑 =====

    @Test
    fun `头像链路不应触碰账户增删改与流水统计`() = runTest {
        val log = mutableListOf<String>()
        val repo = FakeAccountRepository(listOf(WITH_AVATAR), log)
        val storage = FakeAvatarStorage(result = NEW_URI, sharedLog = log)
        val useCase = SetAccountAvatarUseCase(storage, repo)

        useCase(1L, placeholderUri())
        useCase.clear(1L)

        assertEquals("只应用到 findAccount / setAvatarUri", emptyList<String>(), repo.unexpected)
    }

    private companion object {
        const val OLD_URI = "file://avatars/old.jpg"
        const val NEW_URI = "file://avatars/new.jpg"

        val WITH_AVATAR = UserProfileEntity(id = 1L, name = "默认账户", avatarUri = OLD_URI)
        val NO_AVATAR = UserProfileEntity(id = 1L, name = "默认账户")
    }
}
