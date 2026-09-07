package com.example.personalassistant.feature.globalsearch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.globalsearch.domain.usecase.GlobalSearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Phase 8 Sub-step 3：全局搜索 ViewModel
 *
 * 设计要点：
 * - [_queryFlow] 用户输入流（带 250ms debounce，避免边输入边触发 IO）
 * - [useCase] 的 search() 也是 Flow，flatMapLatest 保证只跑最新一次
 * - 当 query 为空时，返回占位空结果
 * - [uiState] 是合并后的 StateFlow，UI 直接 collect
 */
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GlobalSearchViewModel @Inject constructor(
    private val useCase: GlobalSearchUseCase,
) : ViewModel() {

    private val _queryFlow = MutableStateFlow("")

    val uiState: StateFlow<GlobalSearchUiState> = _queryFlow
        .debounce(DEBOUNCE_MS)
        .distinctUntilChanged()
        .flatMapLatest { rawQuery ->
            useCase(rawQuery).map { result ->
                GlobalSearchUiState(
                    query = rawQuery,
                    result = result,
                    isLoading = false,
                    error = null,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = GlobalSearchUiState(),
        )

    /**
     * 用户输入新关键字（UI onValueChange 调用）。
     *
     * - 实时更新 [_queryFlow]，触发 debounce → search → 重新渲染
     */
    fun onQueryChange(newQuery: String) {
        _queryFlow.value = newQuery
    }

    /**
     * 清空输入框（点击 ✕ 图标）。
     */
    fun clearQuery() {
        _queryFlow.value = ""
    }

    companion object {
        /** 输入防抖：250ms 避免每键一查 */
        private const val DEBOUNCE_MS = 250L

        /** UI 离屏 5s 后停止上游 Flow（节省 IO） */
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
