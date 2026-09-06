package com.example.personalassistant.feature.finance.presentation.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.usecase.AddTransactionUseCase
import com.example.personalassistant.feature.finance.domain.usecase.DeleteTransactionUseCase
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
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 7b：新增/编辑流水 Sheet 的 ViewModel
 *
 * - 复用 AddTransactionSheet 做新增和编辑（editingTransaction != null 时为编辑模式）
 * - 编辑模式 save() 走 [UpdateTransactionUseCase]，保证旧 pool 回滚 + 新 pool 应用同事务
 * - 编辑模式可"删除"（DeleteTransactionUseCase）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    observeCategories: ObserveCategoriesUseCase,
    private val addTransaction: AddTransactionUseCase,
    private val updateTransaction: UpdateTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
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
        _state.value = _state.value.copy(
            type = type,
            categoryId = null,
        )
    }

    fun setCategory(categoryId: Long) {
        _state.value = _state.value.copy(categoryId = categoryId)
    }

    fun setAmount(amountText: String) {
        val parsed = amountText.toDoubleOrNull() ?: 0.0
        _state.value = _state.value.copy(amountText = amountText, amount = parsed)
    }

    fun setNote(note: String) {
        _state.value = _state.value.copy(note = note)
    }

    fun setDate(dateMillis: Long) {
        _state.value = _state.value.copy(date = dateMillis)
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val catId = s.categoryId ?: return
        if (s.amount <= 0.0) return
        viewModelScope.launch {
            runCatching {
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
                    )
                } else {
                    // 新增模式：走 add（事务：写入 tx + 应用 pool）
                    addTransaction(
                        type = s.type,
                        amount = s.amount,
                        categoryId = catId,
                        note = s.note.takeIf { it.isNotBlank() },
                        date = s.date,
                    )
                }
            }.onSuccess {
                reset()
                onDone()
            }
        }
    }

    fun deleteCurrent(onDone: () -> Unit) {
        val tx = _state.value.editingTransaction ?: return
        viewModelScope.launch {
            runCatching { deleteTransaction(tx.id) }
                .onSuccess {
                    reset()
                    onDone()
                }
        }
    }

    fun reset() {
        _state.value = UiState()
    }

    fun loadForEdit(transaction: Transaction) {
        _state.value = UiState(
            type = transaction.type,
            categoryId = transaction.categoryId,
            amount = transaction.amount,
            amountText = transaction.amount.toString(),
            note = transaction.note.orEmpty(),
            date = transaction.date,
            editingTransaction = transaction,
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
    ) {
        /** 编辑模式 = editingTransaction != null */
        val isEditing: Boolean get() = editingTransaction != null

        val canSave: Boolean get() = amount > 0.0 && categoryId != null
    }
}