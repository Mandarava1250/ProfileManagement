package com.example.personalassistant.feature.task.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.core.holiday.model.HolidayStrategy
import com.example.personalassistant.core.notification.NotificationPermissionRequester
import com.example.personalassistant.feature.task.domain.model.RepeatRule
import com.example.personalassistant.feature.task.domain.model.RepeatType
import com.example.personalassistant.feature.task.domain.model.SubTask
import com.example.personalassistant.feature.task.domain.model.Task
import com.example.personalassistant.feature.task.domain.model.TaskPriority
import com.example.personalassistant.feature.task.domain.usecase.CancelReminderUseCase
import com.example.personalassistant.feature.task.domain.usecase.DeleteTaskUseCase
import com.example.personalassistant.feature.task.domain.usecase.GetTaskWithSubTasksUseCase
import com.example.personalassistant.feature.task.domain.usecase.ScheduleReminderUseCase
import com.example.personalassistant.feature.task.domain.usecase.UpsertTaskUseCase
import com.example.personalassistant.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 任务编辑页 ViewModel
 *
 * - 构造时从 SavedStateHandle 拿 taskId
 *   - taskId < 0L：新建模式
 *   - taskId > 0L：编辑模式，初始加载 task + subTasks
 * - save() 调 UpsertTaskUseCase，成功后 isSaved = true 触发页面返回
 * - delete() 调 DeleteTaskUseCase，成功后 isSaved = true
 *
 * Phase 4a 新增接线：
 * - save() 成功后调 ScheduleReminderUseCase（按 reminderTime 决定调度）
 * - delete() 成功后调 CancelReminderUseCase
 * - RepeatRule 在 save 时通过 RepeatRule.toRepeatType() 转回 RepeatType enum 写库
 *
 * Phase 5b 新增接线（Compose UI 真接通知权限）：
 * - save() 完成后根据 reminderTime + 权限状态分三路：
 *     1) reminderTime == null           → 直接 scheduleReminder（内部 no-op）
 *     2) reminderTime != null + 已授权   → 直接 scheduleReminder
 *     3) reminderTime != null + 未授权   → 保留 savedTask，置 shouldRequestNotificationPermission=true，等 UI 弹权限框
 * - UI 端 rememberLauncherForActivityResult 的 onResult → onPermissionResult(granted)
 *     - granted=true  → scheduleReminder(savedTask)
 *     - granted=false → 任务照存，errorMessage = 友好提示
 */
/**
 * Phase 5b：通知权限接线层用 open + protected state 暴露，方便测试 fork。
 *
 * 生产用 @HiltViewModel 注入。
 */
@HiltViewModel
open class TaskEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTaskWithSubTasks: GetTaskWithSubTasksUseCase,
    private val upsertTask: UpsertTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val scheduleReminder: ScheduleReminderUseCase,
    private val cancelReminder: CancelReminderUseCase,
    private val notificationPermissionRequester: NotificationPermissionRequester,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>(Screen.EditTask.ARG_TASK_ID) ?: -1L
    private val isNew: Boolean = taskId <= 0L

    /**
     * Phase 5b: 改 `protected` 让单测 / 仪表测试能 fork VM 子类喂初始 state。
     * - 生产代码（HiltViewModel）行为不变：UI 仍只通过 [uiState] 读
     * - Fake 子类在 init { _uiState.value = ... } 直接覆盖
     */
    protected val _uiState = MutableStateFlow(TaskEditUiState(isNew = isNew))
    val uiState: StateFlow<TaskEditUiState> = _uiState.asStateFlow()

    /**
     * Phase 5b：保存成功但 reminder 待权限审批时，暂存要调度的 task。
     * 权限回调 [onPermissionResult] 时取出来 scheduleReminder。
     * 用 var 而非 SavedStateHandle —— 权限回调通常在同一 viewModelScope 内发生。
     */
    private var pendingReminderTask: Task? = null

    init {
        if (isNew) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                val result = getTaskWithSubTasks(taskId)
                if (result == null) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "任务不存在")
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = result.task.title,
                        description = result.task.description.orEmpty(),
                        priority = result.task.priority,
                        dueDate = result.task.dueDate,
                        subTasks = result.subTasks,
                        category = result.task.category,
                        // Phase 4b：从 repeatType + repeatDays 共同还原 RepeatRule
                        repeatRule = RepeatRule.fromRepeatType(
                            result.task.repeatType,
                            customDays = RepeatRule.parseDaysString(result.task.repeatDays),
                        ),
                        reminderTime = result.task.reminderTime,
                        // Phase 8 Sub-step 8：节假日策略落库字段加载
                        holidayStrategy = result.task.holidayStrategy,
                    )
                }
            }
        }
    }

    // ===== 文本字段 =====

    fun onTitleChange(value: String) {
        _uiState.update {
            it.copy(title = value, titleError = if (value.isBlank()) "标题不能为空" else null)
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    // ===== 优先级 =====

    fun onPriorityChange(priority: TaskPriority) {
        _uiState.update { it.copy(priority = priority) }
    }

    // ===== 截止日期 =====

    fun toggleDatePicker() {
        _uiState.update { it.copy(showDatePicker = !it.showDatePicker) }
    }

    fun onDateSelected(dateMillis: Long?) {
        _uiState.update { it.copy(dueDate = dateMillis, showDatePicker = false) }
    }

    fun clearDueDate() {
        _uiState.update { it.copy(dueDate = null) }
    }

    // ===== 分类 =====

    fun onCategoryChange(categoryName: String?) {
        _uiState.update { it.copy(category = categoryName, showCategorySheet = false) }
    }

    fun toggleCategorySheet() {
        _uiState.update { it.copy(showCategorySheet = !it.showCategorySheet) }
    }

    // ===== 重复规则 =====

    fun onRepeatRuleChange(rule: RepeatRule) {
        _uiState.update { it.copy(repeatRule = rule, showRepeatRuleSheet = false) }
    }

    fun toggleRepeatRuleSheet() {
        _uiState.update { it.copy(showRepeatRuleSheet = !it.showRepeatRuleSheet) }
    }

    // ===== 提醒 =====

    fun onReminderChange(millis: Long?) {
        _uiState.update { it.copy(reminderTime = millis, showReminderPicker = false) }
    }

    fun clearReminder() {
        _uiState.update { it.copy(reminderTime = null) }
    }

    fun toggleReminderPicker() {
        _uiState.update { it.copy(showReminderPicker = !it.showReminderPicker) }
    }

    // ===== Phase 8 Sub-step 8：节假日策略 =====

    /**
     * 用户在 UI 上 4 选 1 切换 [HolidayStrategy]
     *
     * 注意：当 [TaskEditUiState.isHolidayStrategyEnabled] == false（非重复任务）时,
     * UI 应当禁用控件,这里仍允许设置值,但不会影响实际行为。
     */
    fun onHolidayStrategyChange(strategy: HolidayStrategy) {
        _uiState.update { it.copy(holidayStrategy = strategy) }
    }

    // ===== 子清单 =====

    fun onNewSubTaskInputChange(value: String) {
        _uiState.update { it.copy(newSubTaskInput = value) }
    }

    fun addSubTask() {
        val title = _uiState.value.newSubTaskInput.trim()
        if (title.isEmpty()) return
        val item = SubTask(
            parentTaskId = if (isNew) null else taskId,
            title = title,
            order = _uiState.value.subTasks.size,
        )
        _uiState.update {
            it.copy(
                subTasks = it.subTasks + item,
                newSubTaskInput = "",
            )
        }
    }

    fun toggleSubTaskComplete(id: Long) {
        _uiState.update { state ->
            state.copy(
                subTasks = state.subTasks.map { item ->
                    if (item.id == id) item.copy(isCompleted = !item.isCompleted) else item
                },
            )
        }
    }

    fun deleteSubTask(id: Long) {
        _uiState.update { state ->
            state.copy(subTasks = state.subTasks.filter { it.id != id })
        }
    }

    // ===== 保存 / 删除 =====

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(titleError = "标题不能为空") }
            return
        }
        viewModelScope.launch {
            try {
                val (repeatType, customDays) = RepeatRule.toRepeatType(state.repeatRule)
                val task = Task(
                    id = if (isNew) 0L else taskId,
                    title = state.title.trim(),
                    description = state.description.takeIf { it.isNotBlank() },
                    priority = state.priority,
                    dueDate = state.dueDate,
                    category = state.category,
                    reminderTime = state.reminderTime,
                    repeatType = repeatType,
                    // Phase 4b：把 CustomWeekdays 序列化为 "MON,..." 字符串，让自定义星期真持久化
                    repeatDays = RepeatRule.formatDaysString(customDays),
                    // Phase 8 Sub-step 8：节假日处理策略落库
                    holidayStrategy = state.holidayStrategy,
                )
                val newRowId = upsertTask(task, state.subTasks)
                val savedTask = task.copy(id = newRowId)
                // Phase 5b：按 reminder + 权限分三路调度
                handleReminderAfterSave(savedTask)
                _uiState.update { it.copy(isSaved = true) }
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(titleError = e.message ?: "保存失败") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "保存失败") }
            }
        }
    }

    /**
     * Phase 5b：保存后调度提醒，按权限状态分三路。
     *
     * 1) [Task.reminderTime] == null          → 直接 scheduleReminder（UseCase 内部 no-op）
     * 2) 已授权                                 → 直接 scheduleReminder
     * 3) 未授权 + 有 reminderTime               → 暂存 savedTask，等权限回调
     */
    private fun handleReminderAfterSave(task: Task) {
        if (task.reminderTime == null) {
            viewModelScope.launch { scheduleReminder(task) }
            return
        }
        viewModelScope.launch {
            if (notificationPermissionRequester.isGranted()) {
                scheduleReminder(task)
            } else {
                pendingReminderTask = task
                _uiState.update {
                    it.copy(shouldRequestNotificationPermission = true)
                }
            }
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            try {
                val task = Task(id = taskId, title = _uiState.value.title)
                deleteTask(task)
                cancelReminder(taskId)
                _uiState.update { it.copy(isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "删除失败") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ===== Phase 5b：通知权限申请 =====

    /**
     * Composable 监听 [TaskEditUiState.shouldRequestNotificationPermission] 时调用后立即清零，
     * 防止重组过程中重复弹权限框。
     *
     * Phase 5b: 改为 open 让 Compose UI 测试可以 override 加计数。
     */
    open fun consumeShouldRequestNotificationPermission() {
        _uiState.update { it.copy(shouldRequestNotificationPermission = false) }
    }

    /**
     * UI 端 rememberLauncherForActivityResult 的 onResult 回调。
     *
     * - [granted] == true  → 调度刚保存的任务
     * - [granted] == false → 任务已存；通过 errorMessage 提示「已保存但不会响铃」
     *
     * 任务在任何情况下都已经被 [UpsertTaskUseCase] 写入，拒绝权限只影响提醒，不丢数据。
     *
     * Phase 5b: 改为 open 让 Compose UI 测试可以 override。
     */
    open fun onPermissionResult(granted: Boolean) {
        consumeShouldRequestNotificationPermission()
        val task = pendingReminderTask
        pendingReminderTask = null
        if (task == null) return
        if (granted) {
            viewModelScope.launch { scheduleReminder(task) }
        } else {
            _uiState.update {
                it.copy(errorMessage = "已保存任务，但通知权限未开启，将不会响铃")
            }
        }
    }
}
