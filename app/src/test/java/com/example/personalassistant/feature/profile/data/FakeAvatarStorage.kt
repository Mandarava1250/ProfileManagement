package com.example.personalassistant.feature.profile.data

import android.net.Uri

/**
 * Phase 4：测试用 Fake [AvatarStorage]（项目「Fake 优先、无 Mockito」约定）
 *
 * ## 它要能回答两个问题
 * 1. **保存成功还是失败** —— [result] 决定；返回 null 正是真机上「图片读不出来」的行为
 * 2. **调用顺序是什么** —— [calls] 按序记录
 *
 * 第 2 条才是重点：本 Phase 要守住的不变量不是「某方法返回值对不对」，而是
 * 「**新头像没落盘成功时，绝不能删旧头像**」。只有记录调用序列才测得出这条 ——
 * 单看最终状态，「先删后写」和「先写后删」长得一模一样。
 *
 * ## 为什么不复用真实的 AvatarStorageImpl
 * 它依赖 `ContentResolver` / `BitmapFactory`。项目虽开了 `isReturnDefaultValues`
 * （不会抛 stub 异常），但那些 API 只会返回默认值 null，于是 `saveAvatar`
 * 永远走「失败」分支 —— **成功路径根本测不到**。
 *
 * @param result     [saveAvatar] 的返回值；null = 模拟「无法解码 / 无权限 / 写盘失败」
 * @param sharedLog  与 [FakeAccountRepository] 共享的调用序列；不传则只记到本类 [calls]。
 *                   「先写库、后删旧文件」是**跨两个对象**的顺序，必须两边注成同一个 list 才看得出来。
 */
class FakeAvatarStorage(
    var result: String? = SAVED_URI,
    private val sharedLog: MutableList<String>? = null,
) : AvatarStorage {

    /** 按序记录的全部调用，形如 `save()` / `delete(file://old.jpg)` */
    val calls: MutableList<String> = mutableListOf()

    private fun log(entry: String) {
        calls.add(entry)
        sharedLog?.add(entry)
    }

    override suspend fun saveAvatar(uri: Uri): String? {
        log("save()")
        return result
    }

    override suspend fun delete(uriString: String) {
        log("delete($uriString)")
    }

    companion object {
        /** 模拟「刚写成的新头像文件」；固定值便于断言写库内容 */
        const val SAVED_URI = "file:///data/user/0/app/files/avatars/new-uuid.jpg"
    }
}

/**
 * JVM 单测里造不出真的 `android.net.Uri`：`Uri.parse(...)` 在
 * `isReturnDefaultValues` 下只返回 null，而 `Uri` 是抽象类，
 * 手写实现要补齐几十个方法（还随 API level 漂移）。
 *
 * 这里用泛型 null 转换绕过 Kotlin 的参数非空检查。之所以安全：
 * [SetAccountAvatarUseCase] 只把这个值**原样转交**给 [AvatarStorage.saveAvatar]，
 * 而 Fake 不读它 —— 被测的是「成功/失败」与「调用顺序」，不是 Uri 内容本身。
 * 真 Uri 的解析行为留给 AVD 手测覆盖（见 DEV_LOG Phase 4 验证结果）。
 *
 * ⚠️ 不要「优化」成直接写 `null as Uri`：那**看着能编译**，但 Kotlin 对具体非空类型的
 * as 会在运行时抛 ClassCastException（`@Suppress("UNCHECKED_CAST")` 只压编译期警告，
 * 管不了这个）。走泛型是因为类型参数擦除后这个 cast 不受检。
 */
internal fun placeholderUri(): Uri = nullAs()

@Suppress("UNCHECKED_CAST")
private fun <T> nullAs(): T = null as T
