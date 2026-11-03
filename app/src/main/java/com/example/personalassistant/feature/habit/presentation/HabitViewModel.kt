package com.example.personalassistant.feature.habit.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.habit.domain.model.Habit
import com.example.personalassistant.feature.habit.domain.model.HabitStats
import com.example.personalassistant.feature.habit.domain.model.HabitTargetType
import com.example.personalassistant.feature.habit.domain.usecase.ArchiveHabitUseCase
import com.example.personalassistant.feature.habit.domain.usecase.CheckInHabitUseCase
import com.example.personalassistant.feature.habit.domain.usecase.DeleteHabitUseCase
import com.example.personalassistant.feature.habit.domain.usecase.ObserveHabitStatsUseCase
import com.example.personalassistant.feature.habit.domain.usecase.ObserveHabitsUseCase
import com.example.personalassistant.feature.habit.domain.usecase.ObserveShowHabitInCalendarUseCase
import com.example.personalassistant.feature.habit.domain.usecase.ObserveTodayHabitsUseCase
import com.example.personalassistant.feature.habit.domain.usecase.SetShowHabitInCalendarUseCase
import com.example.personalassistant.feature.habit.domain.usecase.UpsertHabitUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * Phase 23：习惯页 ViewModel
 *
 * 状态由几条 Room 流 combine 出来：
 * - 今日列表（习惯 + 今天的次数）
 * - 全部习惯（用来列归档）
 * - 选中习惯的统计（连续天数 / 月度表 / 热力图）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HabitViewModel @Inject constructor(
    private val observeTodayHabits: ObserveTodayHabitsUseCase,
    private val observeHabits: ObserveHabitsUseCase,
    private val observeHabitStats: ObserveHabitStatsUseCase,
    private val observeShowInCalendar: ObserveShowHabitInCalendarUseCase,
    private val setShowInCalendar: SetShowHabitInCalendarUseCase,
    private val checkInHabit: CheckInHabitUseCase,
    private val upsertHabit: UpsertHabitUseCase,
    private val deleteHabit: DeleteHabitUseCase,
    private val archiveHabit: ArchiveHabitUseCase,
) : ViewModel() {

    // 每条流只订阅一次 —— 重复调用 useCase 会重复订阅 Room，白白多跑几次查询
    private val todayHabitsFlow = observeTodayHabits()
    private val allHabitsFlow = observeHabits(includeArchived = true)

    private val selectedHabitId = MutableStateFlow<Long?>(null)
    private val displayYearMonth = MutableStateFlow(YearMonth.now())
    private val showArchived = MutableStateFlow(false)
    private val editor = MutableStateFlow<HabitEditorState?>(null)
    private val message = MutableStateFlow<String?>(null)

    /**
     * 真正用于查统计的习惯 id。
     *
     * 用户没手动选过时，默认看列表里的第一个 —— 否则统计区会一直空着，
     * 用户得先点一下才知道自己有连续记录。
     */
    private val effectiveSelectedId: Flow<Long?> =
        combine(todayHabitsFlow, selectedHabitId) { today, selected ->
            selected ?: today.firstOrNull()?.habit?.id
        }

    private val statsFlow: Flow<HabitStats?> =
        combine(effectiveSelectedId, displayYearMonth) { id, ym -> id to ym }
            .flatMapLatest { (id, ym) ->
                if (id == null) {
                    flowOf(null)
                } else {
                    observeHabitStats(id, ym.year, ym.monthValue)
                }
            }

    val uiState: StateFlow<HabitUiState> = combine(
        combine(todayHabitsFlow, allHabitsFlow) { today, all -> today to all },
        combine(effectiveSelectedId, displayYearMonth) { id, ym -> id to ym },
        combine(showArchived, editor, message) { archived, ed, msg -> Triple(archived, ed, msg) },
        combine(statsFlow, observeShowInCalendar()) { stats, inCalendar -> stats to inCalendar },
    ) { habitsPair, selectionPair, misc, displayPair ->
        val (today, all) = habitsPair
        val (selectedId, ym) = selectionPair
        val (archivedVisible, editorState, msg) = misc
        val (stats, inCalendar) = displayPair
        HabitUiState(
            todayItems = today,
            archivedHabits = all.filter { it.isArchived },
            stats = stats,
            selectedHabitId = selectedId,
            displayYear = ym.year,
            displayMonth = ym.monthValue,
            showArchived = archivedVisible,
            editor = editorState,
            showInCalendar = inCalendar,
            message = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HabitUiState(
            displayYear = YearMonth.now().year,
            displayMonth = YearMonth.now().monthValue,
        ),
    )

    // ===== 打卡 =====

    /** 打卡一次（count + 1） */
    fun onCheckIn(habitId: Long) {
        launchGuarded { checkInHabit(habitId) }
    }

    /** 撤销一次打卡（count - 1，减到 0 即取消当天打卡） */
    fun onUndoCheckIn(habitId: Long) {
        launchGuarded { checkInHabit(habitId, delta = -1) }
    }

    // ===== 选择与月份 =====

    /** 选中某个习惯看它的统计 */
    fun onSelectHabit(habitId: Long) {
        selectedHabitId.value = habitId
    }

    /** 上一个月 */
    fun onPrevMonth() {
        displayYearMonth.value = displayYearMonth.value.minusMonths(1)
    }

    /** 下一个月（不超过当前月 —— 未来的月份没有数据，看过去就好） */
    fun onNextMonth() {
        val next = displayYearMonth.value.plusMonths(1)
        val current = YearMonth.now()
        if (!next.isAfter(current)) {
            displayYearMonth.value = next
        }
    }

    // ===== 编辑 =====

    /** 打开「新建习惯」弹窗 */
    fun onStartCreate() {
        editor.value = HabitEditorState()
    }

    /** 打开「编辑习惯」弹窗 */
    fun onStartEdit(habit: Habit) {
        editor.value = HabitEditorState(
            id = habit.id,
            name = habit.name,
            unit = habit.unit.orEmpty(),
            targetType = habit.targetType,
            targetCount = habit.targetCount.toString(),
            iconKey = habit.iconKey,
            color = habit.color,
        )
    }

    /** 关闭弹窗 */
    fun onDismissEditor() {
        editor.value = null
    }

    /** 弹窗里的字段变化 */
    fun onEditorChange(state: HabitEditorState) {
        editor.value = state
    }

    /**
     * 保存弹窗里的习惯。
     *
     * 校验失败（名称为空 / 目标次数非法）时把原因显示出来，不静默吞掉。
     */
    fun onSaveEditor() {
        val current = editor.value ?: return
        launchGuarded {
            val targetCount = current.targetCount.trim().toIntOrNull()
            if (targetCount == null || targetCount < 1) {
                message.value = "目标次数要填一个 ≥ 1 的整数"
                return@launchGuarded
            }
            val habit = Habit(
                id = current.id,
                name = current.name.trim(),
                iconKey = current.iconKey,
                color = current.color,
                targetType = current.targetType,
                targetCount = targetCount,
                unit = current.unit.trim().ifBlank { null },
                isArchived = false,
            )
            upsertHabit(habit)
            editor.value = null
        }
    }

    /** 删除习惯 */
    fun onDelete(habit: Habit) {
        launchGuarded {
            deleteHabit(habit)
            message.value = "已删除「${habit.name}」"
        }
    }

    /** 归档 / 取消归档 */
    fun onToggleArchive(habit: Habit) {
        launchGuarded {
            archiveHabit(habit.id, !habit.isArchived)
            message.value = if (habit.isArchived) "已恢复「${habit.name}」" else "已归档「${habit.name}」"
        }
    }

    /** 展开 / 收起「已归档」区 */
    fun onToggleShowArchived() {
        showArchived.value = !showArchived.value
    }

    // ===== 23.6 日历同屏开关 =====

    /** 设置「习惯在日历同屏显示」 */
    fun onSetShowInCalendar(enabled: Boolean) {
        launchGuarded { setShowInCalendar(enabled) }
    }

    /** 关掉一次性提示 */
    fun onDismissMessage() {
        message.value = null
    }

    /**
     * 统一跑一次可能抛错的操作，把异常消息显示出来。
     *
     * 「合法但被拒绝」的分支（校验不过、DB 报错）必须让用户看见原因 ——
     * 静默吞掉的话用户会以为「点了没反应」。
     */
    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { error ->
                    message.value = error.message ?: "操作失败"
                }
        }
    }
}

/** 目标类型的中文展示名 */
internal fun HabitTargetType.displayName(): String = when (this) {
    HabitTargetType.DAILY -> "每天"
    HabitTargetType.WEEKLY -> "每周"
}
