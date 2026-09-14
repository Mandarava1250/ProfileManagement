package com.example.personalassistant.feature.finance.presentation.add

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import com.example.personalassistant.core.storage.AttachmentStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Phase 2：测试用假附件存储（不依赖真实 Context / IO）
 *
 * ## 设计动机
 * - 项目「Fake 优先」风格：不引 Mockito / mockk，所有依赖手写 Fake
 * - [AttachmentStore] 是 open class（Hilt @Singleton），可通过子类覆盖 [saveAll] / [delete]
 * - 父类 [AttachmentStore] 构造需要 [Context] 和 [CoroutineDispatcher]，
 *   但 [FakeAttachmentStore] 覆盖了 saveAll/delete 后，父类的 attachmentDir.get()
 *   永远不会被调用，因此可以用 [ContextWrapper] 占位构造
 *
 * ## 用法
 * ```kotlin
 * val fakeStore = FakeAttachmentStore()
 * val vm = AddTransactionViewModel(..., attachmentStore = fakeStore)
 * ```
 *
 * ## 行为
 * - saveAll 任何 uris 都返回空列表 → VM 写入 attachmentUri = null
 * - delete 是 no-op
 */
class FakeAttachmentStore(
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : AttachmentStore(
    context = NoOpContext,
    dispatcher = dispatcher,
) {
    override suspend fun saveAll(uris: List<Uri>): List<String> = emptyList()

    override suspend fun delete(uriString: String) = Unit

    private companion object {
        /**
         * 一个空 Context：仅用于满足父类构造签名。
         * [FakeAttachmentStore] 不调用任何 Context 方法，因此实现留空。
         * 继承 [ContextWrapper] 而不是直接实现 [Context]，避免实现几十个抽象方法。
         */
        val NoOpContext: Context = object : ContextWrapper(null) {}
    }
}