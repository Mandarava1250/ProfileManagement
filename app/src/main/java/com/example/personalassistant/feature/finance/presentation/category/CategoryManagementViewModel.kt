package com.example.personalassistant.feature.finance.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.domain.repository.CategoryInUseException
import com.example.personalassistant.feature.finance.domain.usecase.DeleteCategoryUseCase
import com.example.personalassistant.feature.finance.domain.usecase.ObserveCategoriesUseCase
import com.example.personalassistant.feature.finance.domain.usecase.UpsertCategoryUseCase
import com.example.personalassistant.feature.finance.presentation.util.Feedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * C 重构阶段 5/11：分类管理 ViewModel
 *
 * A 增量：
 * - [rename] 改名
 * - [changeColor] 改颜色
 * - [addCategory] 现在接收 iconName / color（不再写死）
 *
 * D-β：所有公开方法最终都进入 [process] reducer；公开签名保留，旧测试不破。
 *
 * Feedback：原 `MutableStateFlow<String?>` 改为 `MutableStateFlow<Feedback?>`，
 * 三档语义见 [com.example.personalassistant.feature.finance.presentation.util.Feedback]。
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

    /** 用户最近一次操作的反馈 */
    private val _feedback = MutableStateFlow<Feedback?>(null)
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    fun clearFeedback() {
        _feedback.value = null
    }

    /** C-A：新增分类时由 UI 传入选好的图标与颜色 */
    fun addCategory(name: String, type: TransactionType, iconName: String, color: Int) {
        process(Intent.AddCategory(name, type, iconName, color))
    }

    /** 兼容老签名：缺省图标 / 颜色（UI 尚未接 ColorPickerSheet 的中间过渡版本用） */
    @Suppress("unused")
    fun addCategory(name: String, type: TransactionType) {
        addCategory(name, type, iconName = "Category", color = 0xFF6750A4.toInt())
    }

    fun deleteCategory(category: Category) {
        process(Intent.DeleteCategory(category))
    }

    fun setIcon(category: Category, iconName: String) {
        process(Intent.SetIcon(category, iconName))
    }

    /** C-A：重命名 */
    fun rename(category: Category, newName: String) {
        process(Intent.Rename(category, newName))
    }

    /** C-A：改颜色 */
    fun changeColor(category: Category, color: Int) {
        process(Intent.ChangeColor(category, color))
    }

    // ============ 内部 reducer ============

    private fun process(intent: Intent) {
        when (intent) {
            is Intent.AddCategory -> viewModelScope.launch {
                runCatching {
                    upsertCategory(
                        Category(
                            id = 0L,
                            name = intent.name.trim(),
                            type = intent.type,
                            iconName = intent.iconName,
                            color = intent.color,
                            isDefault = false,
                            sortOrder = 99,
                        )
                    )
                }.onSuccess { _feedback.value = Feedback.Success("已添加分类：${intent.name}") }
                    .onFailure { _feedback.value = Feedback.Error("添加失败：${it.message ?: "未知错误"}", cause = it) }
            }

            is Intent.DeleteCategory -> viewModelScope.launch {
                deleteCategory(intent.category.id)
                    .onSuccess { _feedback.value = Feedback.Success("已删除：${intent.category.name}") }
                    .onFailure { e ->
                            _feedback.value = when (e) {
                                is CategoryInUseException -> Feedback.Warning(
                                    message = "${intent.category.name} 还有 ${e.transactionCount} 笔交易，无法删除",
                                    cause = e,
                                )
                                else -> Feedback.Error(
                                    message = "删除失败：${e.message ?: "未知错误"}",
                                    cause = e,
                                )
                            }
                        }
            }

            is Intent.SetIcon -> viewModelScope.launch {
                runCatching {
                    upsertCategory(intent.category.copy(iconName = intent.iconName))
                }.onSuccess { _feedback.value = Feedback.Success("已更新图标：${intent.category.name}") }
                    .onFailure { _feedback.value = Feedback.Error("更新图标失败：${it.message ?: "未知错误"}", cause = it) }
            }

            is Intent.Rename -> viewModelScope.launch {
                val trimmed = intent.newName.trim()
                if (trimmed.isEmpty()) {
                    _feedback.value = Feedback.Warning("分类名不能为空")
                    return@launch
                }
                runCatching {
                    upsertCategory(intent.category.copy(name = trimmed))
                }.onSuccess { _feedback.value = Feedback.Success("已重命名为：$trimmed") }
                    .onFailure { _feedback.value = Feedback.Error("重命名失败：${it.message ?: "未知错误"}", cause = it) }
            }

            is Intent.ChangeColor -> viewModelScope.launch {
                runCatching {
                    upsertCategory(intent.category.copy(color = intent.color))
                }.onSuccess { _feedback.value = Feedback.Success("已更新颜色：${intent.category.name}") }
                    .onFailure { _feedback.value = Feedback.Error("更新颜色失败：${it.message ?: "未知错误"}", cause = it) }
            }
        }
    }

    /** D-β：所有用户意图的统一入口 */
    private sealed interface Intent {
        data class AddCategory(
            val name: String,
            val type: TransactionType,
            val iconName: String,
            val color: Int,
        ) : Intent
        data class DeleteCategory(val category: Category) : Intent
        data class SetIcon(val category: Category, val iconName: String) : Intent
        data class Rename(val category: Category, val newName: String) : Intent
        data class ChangeColor(val category: Category, val color: Int) : Intent
    }
}
