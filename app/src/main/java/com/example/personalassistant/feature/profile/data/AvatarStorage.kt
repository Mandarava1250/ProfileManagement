package com.example.personalassistant.feature.profile.data

import android.net.Uri

/**
 * Phase 4：账户头像文件存储（接口）
 *
 * ## 为什么是接口而不是直接用实现类
 * [SetAccountAvatarUseCase] 要执行「保存新头像 → 写库 → 删旧头像」这条**顺序敏感**的链路，
 * 其中最容易出错的一环是：新头像没存成功就把旧的删了 —— 用户一次误操作就永久丢了头像。
 * 这条不变量必须能在 JVM 里测。而真实存储依赖 `ContentResolver` + `Bitmap`，
 * 即使项目开了 `isReturnDefaultValues`（不会抛异常），那些 API 也只会返回 null，
 * 测不出「成功/失败」两条分支。
 * 所以 UseCase 依赖本接口，测试注入 `FakeAvatarStorage`；实现类只在 DI 与真机 IO 里出现。
 *
 * ## 为什么 saveAvatar 收 Uri、delete 收字符串
 * [saveAvatar] 必须收 [Uri]（PhotoPicker 给的原始图片位置），这一步只能真机做；
 * 而 [delete] 收 URI 字符串 —— 那是库里已存的值，删除是纯文件系统操作，
 * 用 Fake 记录调用序列即可验证「该删的时候删了、不该删的时候没删」。
 */
interface AvatarStorage {

    /**
     * 把 [uri] 指向的图片压成头像尺寸写入头像目录。
     *
     * @return 成功时返回 `file://` URI 字符串（应写入 `UserProfileEntity.avatarUri`）；
     *         **任何失败返回 null**（无法解码 / 无权限 / 写盘失败），调用方据此保持原状：
     *         既不写库，也不删旧文件。
     */
    suspend fun saveAvatar(uri: Uri): String?

    /** 删除一个头像文件（按 `file://` URI 字符串）。不在头像目录内或文件不存在时静默 no-op。 */
    suspend fun delete(uriString: String)
}
