package com.example.personalassistant.feature.finance.presentation.add

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.storage.AttachmentStore
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.usecase.AddTransactionUseCase
import com.example.personalassistant.feature.finance.domain.usecase.DeleteTransactionUseCase
import com.example.personalassistant.feature.finance.domain.usecase.DeleteTransferUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCategoriesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.UpdateTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 7b + Phase 2：新增/编辑流水 Sheet 的 ViewModel
 *
 * ## 7b
 * - 复用 AddTransactionSheet 做新增和编辑（editingTransaction != null 时为编辑模式）
 * - 编辑模式 save() 走 [UpdateTransactionUseCase]，保证旧 pool 回滚 + 新 pool 应用同事务
 * - 编辑模式可"删除"（DeleteTransactionUseCase）
 *
 * ## Phase 2：附件上传
 * - [AttachmentSection] 用 PhotoPicker 让用户最多选 [MAX_ATTACHMENTS] 张图
 * - onPickedAttachments 把 Picker 返回的原始 Uri 追加到 state.attachmentUris（未压缩）
 * - save() 时调 [AttachmentStore.saveAll] 压缩到 1080p JPEG → 返回 file:// URI 列表
 * - 多个 file:// URI 用 [URI_DELIMITER]（逗号）拼接写入 TransactionEntity.attachmentUri
 * - file:// URI 本身不含逗号，所以安全
 *
 * ## 取舍：未提交的附件
 * - 用户选了图但最后没保存 → 压缩后的图片文件会留在 filesDir/attachments/
 * - 这是已知小内存泄漏，Phase 3+ 优化（重置时比对增量 URI 删除）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    observeCategories: ObserveCategoriesUseCase,
    private val addTransaction: AddTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val deleteTransfer: DeleteTransferUseCase,
    private val attachmentStore: AttachmentStore,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 分类列表：跟随 state.type 变化，自动按当前类型过滤 */
    val categories: StateFlow<List<Category>> = _state
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> observeCategories.byType(type) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 用户切换 支出/收入 类型 */
    fun setType(type: TransactionType) {
        _state.update {
            it.copy(type = type, categoryId = null)
        }
    }

    fun setCategory(categoryId: Long) {
        _state.update { it.copy(categoryId = categoryId) }
    }

    fun setAmount(amountText: String) {
        val parsed = amountText.toDoubleOrNull() ?: 0.0
        _state.update { it.copy(amountText = amountText, amount = parsed) }
    }

    fun setNote(note: String) {
        _state.update { it.copy(note = note) }
    }

    fun setDate(dateMillis: Long) {
        _state.update { it.copy(date = dateMillis) }
    }

    /**
     * Picker 返回的原始 Uri：追加到 state.attachmentUris（还没压缩落盘）
     * - 重复 Uri 去重（按 toString）
     * - 超过 [MAX_ATTACHMENTS] 则截断
     */
    fun onPickedAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { current ->
            val merged = (current.attachmentUris + uris).distinctBy { it.toString() }
            current.copy(attachmentUris = merged.take(MAX_ATTACHMENTS))
        }
    }

    /** 按索引删除附件（仅从 state 移除，磁盘文件保留至下次清理） */
    fun onRemoveAttachment(index: Int) {
        _state.update { current ->
            if (index !in current.attachmentUris.indices) current
            else current.copy(
                attachmentUris = current.attachmentUris.toMutableList().apply { removeAt(index) }
            )
        }
    }

    fun save(onDone: () -> Unit) {
        // 每次提交先清掉上一轮的错误，否则错文会一直在屏上挂着
        _state.value = _state.value.copy(error = null)
        val s = _state.value
        val catId = s.categoryId ?: return
        if (s.amount <= 0.0) return
        viewModelScope.launch {
            runCatching {
                // 先压缩附件（如果有）→ 得到 file:// URI 列表
                val savedUriStrings = attachmentStore.saveAll(s.attachmentUris)
                val attachmentField = savedUriStrings
                    .joinToString(URI_DELIMITER)
                    .takeIf { it.isNotEmpty() }

                val editing = s.editingTransaction
                if (editing != null) {
                    // 编辑模式：走 update（事务：退旧 pool + 应用新 pool）
                    updateTransaction(
                        id = editing.id,
                        type = s.type,
                        amount = s.amount,
                        categoryId = catId,
                        note = s.note.takeIf { it.isNotBlank() },
                        date = s.date,
                        attachmentUri = attachmentField,
                    )
                } else {
                    // 新增模式：走 add（事务：写入 tx + 应用 pool）
                    addTransaction(
                        type = s.type,
                        amount = s.amount,
                        categoryId = catId,
                        note = s.note.takeIf { it.isNotBlank() },
                        date = s.date,
                        attachmentUri = attachmentField,
                    )
                }
            }.onSuccess {
                reset()
                onDone()
            }.onFailure {
                // 不接住就是「点了保存没反应」；转账的业务拦截全走这里
                _state.value = _state.value.copy(error = it.message ?: "保存失败")
            }
        }
    }

    fun deleteCurrent(onDone: () -> Unit) {
        val tx = _state.value.editingTransaction ?: return
        _state.value = _state.value.copy(error = null)
        viewModelScope.launch {
            runCatching {
                // Phase 5 分流：转账必须成对删（走普通删会撞 Repository 的守卫）。
                // 不分开的话：Repository 里的 require 抛异常 → 被 runCatching 吃掉
                // → 用户点删除什么都没发生，也不报错 —— 静默失败比崩溃更难发现。
                if (tx.type.isTransfer) {
                    val n = deleteTransfer(tx.id)
                    if (n == 0) {
                        throw IllegalStateException("没找到这笔转账")
                    }
                } else {
                    deleteTransaction(tx.id)
                }
            }.onSuccess {
                reset()
                onDone()
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "删除失败")
            }
        }
    }

    fun reset() {
        _state.value = UiState()
    }

    fun loadForEdit(transaction: Transaction) {
        // 解析多 URI（逗号分隔，单张也走 split → listOf(uri)）
        val attachmentUris = transaction.attachmentUri
            ?.split(URI_DELIMITER)
            ?.filter { it.isNotBlank() }
            ?.map { it.toUri() }
            ?: emptyList()
        _state.value = UiState(
            type = transaction.type,
            categoryId = transaction.categoryId,
            amount = transaction.amount,
            amountText = transaction.amount.toString(),
            note = transaction.note.orEmpty(),
            date = transaction.date,
            editingTransaction = transaction,
            attachmentUris = attachmentUris,
        )
    }

    data class UiState(
        val type: TransactionType = TransactionType.EXPENSE,
        val categoryId: Long? = null,
        val amount: Double = 0.0,
        val amountText: String = "",
        val note: String = "",
        val date: Long = System.currentTimeMillis(),
        val editingTransaction: Transaction? = null,
        /** 待提交的原始 URI（PhotoPicker 返回的 content:// URI，未压缩） */
        val attachmentUris: List<Uri> = emptyList(),
        /**
         * Phase 5：写失败的原因（可直接显示给用户）。
         *
         * 为什么现在才加：save/delete 一直用 `runCatching` 但只接 `onSuccess`，
         * 失败时**什么都不发生也不报错**。收支流水很少失败所以没人发现；
         * 转账有一堆业务拦截（余额不足 / 目标已删 / 缺转账分类），不接住就全成静默失败。
         */
        val error: String? = null,
    ) {
        /** 编辑模式 = editingTransaction != null */
        val isEditing: Boolean get() = editingTransaction != null

        /**
         * 编辑中的是转账：不得提交修改。
         *
         * Repository 与 DAO 两层都会拒编辑转账（会留下半对），UI 必须先把这条路堵上：
         * 否则用户改了金额点保存，只得到一个错误文案，却不知道根本改不了。
         */
        val isEditingTransfer: Boolean
            get() = editingTransaction?.type?.isTransfer == true

        val canSave: Boolean get() = amount > 0.0 && categoryId != null && !isEditingTransfer
    }

    companion object {
        const val MAX_ATTACHMENTS = 3
        private const val URI_DELIMITER = ","
    }
}