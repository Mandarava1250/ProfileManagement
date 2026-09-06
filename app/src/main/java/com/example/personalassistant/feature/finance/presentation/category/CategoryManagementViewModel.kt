package com.example.personalassistant.feature.finance.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.CategoryInUseException
import com.example.personalassistant.feature.finance.domain.usecase.DeleteCategoryUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCategoriesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.UpsertCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 7a：分类管理 ViewModel
 *
 * - 列出全部分类（支出 + 收入）
 * - 支持新增 / 删除（带引用检查）
 */
@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    observeCategories: ObserveCategoriesUseCase,
    private val upsertCategory: UpsertCategoryUseCase,
    private val deleteCategory: DeleteCategoryUseCase,
) : ViewModel() {

    /** 全部分类 */
    val categories: StateFlow<List<Category>> = observeCategories.all()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 用户最近一次操作的反馈（成功 / 失败 / 警告） */
    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    fun addCategory(name: String, type: TransactionType) {
        viewModelScope.launch {
            runCatching {
                upsertCategory(
                    Category(
                        id = 0L,
                        name = name.trim(),
                        type = type,
                        iconName = "Category",
                        color = 0xFF6750A4.toInt(),
                        isDefault = false,
                        sortOrder = 99,
                    )
                )
            }.onSuccess { _feedback.value = "已添加分类：$name" }
                .onFailure { _feedback.value = "添加失败：${it.message ?: "未知错误"}" }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            deleteCategory(category.id)
                .onSuccess { _feedback.value = "已删除：${category.name}" }
                .onFailure { e ->
                    _feedback.value = when (e) {
                        is CategoryInUseException -> "${category.name} 还有 ${e.transactionCount} 笔交易，无法删除"
                        else -> "删除失败：${e.message ?: "未知错误"}"
                    }
                }
        }
    }
}
