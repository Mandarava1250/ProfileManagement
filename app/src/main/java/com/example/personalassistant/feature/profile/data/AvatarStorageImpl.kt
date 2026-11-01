package com.example.personalassistant.feature.profile.data

import android.content.Context
import android.net.Uri
import com.example.personalassistant.core.di.IoDispatcher
import com.example.personalassistant.core.storage.AVATAR_MAX_LONG_EDGE_PX
import com.example.personalassistant.core.storage.AVATAR_SUBDIR
import com.example.personalassistant.core.storage.deleteFileInsideDir
import com.example.personalassistant.core.storage.encodeImageToJpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 4：头像文件存储实现（写进 `filesDir/avatars`，压到 512px JPEG）
 *
 * ## 为什么单独一个子目录，不跟 Phase 2 的 attachments 混
 * [com.example.personalassistant.core.storage.AttachmentStore] 上挂着
 * `deleteAllInDir()` —— 一个「清空该目录全部文件」的核按钮。
 * 若头像也放 `attachments/`，任何将来调用它的清理逻辑都会连头像一起删掉，
 * 而库里 `avatarUri` 还在 → 头像永久 404。目录隔开，这条误删路径就不存在。
 *
 * ## 为什么最长边 512 而非附件的 1080
 * 头像在 UI 里最大约 48–56dp（3x 密度 ≈ 168px），512 已留足余量；
 * 同张图 512/85 比 1080/85 小约 4 倍，账户数一多差别明显。
 *
 * ## 可测性说明
 * 本类**不被 JVM 测试直接覆盖**（依赖 `ContentResolver` / `Bitmap`，
 * 项目虽开了 `isReturnDefaultValues`，那些 API 只会返回 null，测不出成功分支）。
 * [AccountRepositoryImpl] 依赖的是 [AvatarStorage] 接口，测的是**调用顺序与守卫**；
 * 本类的真实读写与目录穿越保护留给 AVD 手测，台账如实记 🟨。
 */
@Singleton
class AvatarStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : AvatarStorage {

    private val avatarDir: File
        get() = File(context.filesDir, AVATAR_SUBDIR).apply {
            if (!exists()) mkdirs()
        }

    override suspend fun saveAvatar(uri: Uri): String? = withContext(dispatcher) {
        encodeImageToJpeg(
            resolver = context.contentResolver,
            uri = uri,
            dir = avatarDir,
            maxLongEdge = AVATAR_MAX_LONG_EDGE_PX,
        )
    }

    override suspend fun delete(uriString: String): Unit = withContext(dispatcher) {
        // 只删 avatars 目录内的文件；传进来的是 attachments 的 URI 也会被拒
        deleteFileInsideDir(uriString, avatarDir)
        Unit
    }
}
