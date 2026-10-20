package com.example.personalassistant.feature.me.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalassistant.feature.me.domain.model.GpaRule
import com.example.personalassistant.feature.me.domain.model.StudentProfile
import com.example.personalassistant.feature.me.domain.repository.StudentProfileRepository
import com.example.personalassistant.feature.me.domain.usecase.CalculateGpaUseCase
import com.example.personalassistant.feature.me.domain.usecase.GpaReverseResult
import com.example.personalassistant.feature.me.domain.usecase.GpaSummary
import com.example.personalassistant.feature.me.domain.usecase.ReverseCalculateGpaUseCase
import com.example.personalassistant.feature.profile.data.local.AccountIds
import com.example.personalassistant.feature.timetable.domain.model.Course
import com.example.personalassistant.feature.timetable.domain.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「我」页 ViewModel
 *
 * 职责：
 *  - 聚合 student profile + 当前学期 + 全部课程 → 暴露 UiState
 *  - GPA 由 [CalculateGpaUseCase] 计算（自动排除当前学期）
 *  - 编辑"目标 GPA / 个人陈述 / GPA 规则"即时 upsert
 *  - GF 反推：把"已修学分 + 现有 GPA + 未来学分 + 目标 GPA"喂给 [ReverseCalculateGpaUseCase]
 *
 * 当前只支持默认账户（[AccountIds.DEFAULT_ACCOUNT_ID]）；多账户下需要替换为 Hilt-provided
 * CurrentAccountRepository.flow。
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    private val profileRepository: StudentProfileRepository,
    timetableRepository: TimetableRepository,
    private val calculateGpa: CalculateGpaUseCase,
    private val reverseCalculateGpa: ReverseCalculateGpaUseCase,
) : ViewModel() {

    private val accountId = AccountIds.DEFAULT_ACCOUNT_ID

    val uiState: StateFlow<MeUiState> = combine(
        profileRepository.observeProfile(accountId),
        timetableRepository.observeAllCourses(),
        timetableRepository.observeCurrentSemester(),
    ) { profile, courses, currentSemester ->
        buildState(
            profile = profile,
            courses = courses,
            currentSemesterName = currentSemester?.name,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MeUiState(),
    )

    fun updateGpaTarget(target: Double) {
        viewModelScope.launch {
            val current = profileRepository.getProfile(accountId) ?: defaultProfile()
            profileRepository.upsertProfile(
                current.copy(
                    gpaTarget = target.coerceIn(0.0, MAX_GPA_INPUT),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    fun updateResumeText(text: String) {
        viewModelScope.launch {
            val current = profileRepository.getProfile(accountId) ?: defaultProfile()
            profileRepository.upsertProfile(
                current.copy(
                    resumeText = text,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /** 规则编辑页保存时调用。rule 已通过 init 校验。 */
    fun updateGpaRule(rule: GpaRule) {
        viewModelScope.launch {
            val current = profileRepository.getProfile(accountId) ?: defaultProfile()
            profileRepository.upsertProfile(
                current.copy(
                    gpaRule = rule,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun defaultProfile() = StudentProfile(
        accountId = accountId,
        gpaTarget = 4.0,
        resumeText = "",
        gpaRule = GpaRule.DEFAULT,
        updatedAt = System.currentTimeMillis(),
    )

    private fun buildState(
        profile: StudentProfile?,
        courses: List<Course>,
        currentSemesterName: String?,
    ): MeUiState {
        val gpaTarget = profile?.gpaTarget ?: 4.0
        val resumeText = profile?.resumeText.orEmpty()
        val gpaRule = profile?.gpaRule ?: GpaRule.DEFAULT
        val gpaSummary = calculateGpa(
            courses = courses,
            excludedSemester = currentSemesterName,
        )

        // ===== GF 反推（按伙伴给的公式） =====
        // S = 已修学分 = gpaSummary.totalCredits
        // G0 = 现有 GPA = gpaSummary.gpa
        // F = 未来还要修的学分：当前没有专门的"未来课程"概念 → 用「目标 - 已修」近似，
        //     或在没有 F 数据时（gpaSummary.totalCredits 已经覆盖到 gpaTarget 范围）→ 返回 NoFutureCredits
        // 这里采取保守做法：F = 0 → 报 NoFutureCredits（规则 3）
        // 未来若增加"未来课程清单"表，替换 F 即可
        val reverse = if (gpaSummary != null) {
            reverseCalculateGpa(
                earnedCredits = gpaSummary.totalCredits,
                currentGpa = gpaSummary.gpa,
                futureCredits = 0.0, // 暂无未来课程概念；F=0 走规则 3
                targetGpa = gpaTarget,
                rule = gpaRule,
            )
        } else {
            null
        }

        return MeUiState(
            gpaTarget = gpaTarget,
            gpaRule = gpaRule,
            gpaSummary = gpaSummary,
            reverseResult = reverse,
            currentSemesterName = currentSemesterName,
            resumeText = resumeText,
        )
    }

    companion object {
        /** 输入框上限：放宽到 5.0 以兼容 5.0 制学校（rule.scaleMax 才是硬上限） */
        const val MAX_GPA_INPUT = 5.0
    }
}

data class MeUiState(
    /** 目标 GPA（用户输入） */
    val gpaTarget: Double = 4.0,
    /** GPA 计算规则（学校定制） */
    val gpaRule: GpaRule = GpaRule.DEFAULT,
    /** GPA 计算结果（null = 没有有效成绩） */
    val gpaSummary: GpaSummary? = null,
    /** GF 反推结果（null = 还没算，比如没有 GPA 数据） */
    val reverseResult: GpaReverseResult? = null,
    /** 当前学期（用于显示"已排除 X"） */
    val currentSemesterName: String? = null,
    /** 个人陈述 */
    val resumeText: String = "",
)
