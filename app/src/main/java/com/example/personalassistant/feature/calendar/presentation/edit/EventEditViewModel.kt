package com.example.personalassistant.feature.calendar.presentation.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.calendar.domain.model.Event
import com.example.personalassistant.feature.calendar.domain.model.EventCategory
import com.example.personalassistant.feature.calendar.domain.repository.CalendarRepository
import com.example.personalassistant.feature.calendar.domain.usecase.ObserveEventCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Phase 8 收尾（plan 8final §2.1）通知 deeplink 专用 ViewModel
 *
 * **为什么独立一个 VM**：
 * - 主 [com.example.personalassistant.feature.calendar.presentation.CalendarViewModel]
 *   内嵌的 `EditEventSheet` 是 BottomSheet 形态，且其 `editingState` 与 CalendarScreen
 *   强耦合（月/周/日视图选中态等），不适合从独立 NavBackStackEntry 直接复用
 * - 本 VM 只做"按 id 加载事件 + 列出分类"两件事，**只读**，不接管保存/删除
 * - 保存入口跳转到 Calendar 主屏由用户主动操作（避免 deeplink 直接改数据）
 */
@HiltViewModel
class EventEditViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val observeEventCategories: ObserveEventCategoriesUseCase,
) : ViewModel() {

    private val _event = MutableStateFlow<Event?>(null)
    val event: StateFlow<Event?> = _event.asStateFlow()

    /** null = 加载中；非 null = 加载后的状态（null 表示事件不存在） */
    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    val eventCategories: StateFlow<List<EventCategory>> = observeEventCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 按 id 加载事件。
     * 加载完成后 [loadState] 变为 Loaded(event != null)。
     */
    fun loadEvent(eventId: Long) {
        if (eventId <= 0L) {
            _loadState.value = LoadState.NotFound("事件 id 无效：$eventId")
            _event.value = null
            return
        }
        viewModelScope.launch {
            _loadState.value = LoadState.Loading
            val loaded = calendarRepository.getEventById(eventId)
            _event.value = loaded
            _loadState.value = if (loaded != null) LoadState.Loaded
            else LoadState.NotFound("事件不存在（id=$eventId，可能已被删除）")
        }
    }

    /** 关闭错误状态（让用户回到只读详情） */
    fun dismissError() {
        _loadState.value = LoadState.Loaded
    }

    sealed interface LoadState {
        data object Loading : LoadState
        data object Loaded : LoadState
        data class NotFound(val message: String) : LoadState
    }
}