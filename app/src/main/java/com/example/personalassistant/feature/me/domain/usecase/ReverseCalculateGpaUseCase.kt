package com.example.personalassistant.feature.me.domain.usecase

import com.example.personalassistant.feature.me.domain.model.GpaRule
import javax.inject.Inject

/**
 * GPA 反推 —— 在已知过去 GPA + 目标 GPA + 未来学分时，
 * 算出未来课程需要达到的平均绩点 GF
 *
 * ## 变量
 *  - S = 已修且纳入绩点计算的学分总和
 *  - G0 = 现有累计 GPA
 *  - F = 未来还要修且纳入绩点计算的学分总和
 *  - GT = 目标累计 GPA
 *  - GF = 未来课程所需达到的平均绩点
 *
 * ## 公式
 *  `GF = [GT × (S + F) − G0 × S] / F`
 *
 * ## 执行规则
 *  1. 课程范围、绩点转换表、重修规则、P/F 课程处理、舍入规则——所有口径必须一致
 *  2. 只统计纳入绩点计算的课程学分
 *  3. F = 0 → 返回 [GpaReverseResult.NoFutureCredits]
 *  4. GF > rule.scaleMax → [GpaReverseResult.Impossible]（附带最多可达）
 *  5. GF < rule.scaleMin → [GpaReverseResult.Trivial]（附带最低可行）
 *  6. 中间计算保留足够精度，最终按用户指定规则舍入；未指定 → 保留 2 位小数
 *  7. 缺学校规则时先列缺失项询问，不得自行假设
 *
 * Phase 12 扩展
 */
class ReverseCalculateGpaUseCase @Inject constructor() {

    operator fun invoke(
        earnedCredits: Double,
        currentGpa: Double,
        futureCredits: Double,
        targetGpa: Double,
        rule: GpaRule = GpaRule.DEFAULT,
    ): GpaReverseResult {
        // 规则 3：F = 0，无法反推
        if (futureCredits <= 0.0) {
            return GpaReverseResult.NoFutureCredits
        }

        // 规则 1：口径校验
        if (earnedCredits < 0.0 || currentGpa < 0.0 || targetGpa < 0.0) {
            return GpaReverseResult.InvalidInput("输入数据非法（学分/GPA 不能为负）")
        }
        if (currentGpa > rule.scaleMax) {
            return GpaReverseResult.InvalidInput(
                "现有 GPA ($currentGpa) 超出规则最高绩点 (${rule.scaleMax})",
            )
        }
        if (targetGpa > rule.scaleMax) {
            return GpaReverseResult.InvalidInput(
                "目标 GPA ($targetGpa) 超出规则最高绩点 (${rule.scaleMax})",
            )
        }

        // 公式：GF = [GT × (S + F) − G0 × S] / F
        val raw = (targetGpa * (earnedCredits + futureCredits) - currentGpa * earnedCredits) / futureCredits

        // 规则 6：保留 2 位小数（四舍五入）
        val rounded = roundTo2(raw)

        // 规则 4：超过最高绩点
        if (rounded > rule.scaleMax) {
            return GpaReverseResult.Impossible(
                requiredGpa = rounded,
                maxAchievable = roundTo2(rule.scaleMax),
                scaleMax = rule.scaleMax,
            )
        }

        // 规则 5：低于最低绩点（很容易达到）
        if (rounded < rule.scaleMin) {
            return GpaReverseResult.Trivial(
                requiredGpa = rounded,
                minRequired = roundTo2(rule.scaleMin),
                scaleMin = rule.scaleMin,
            )
        }

        return GpaReverseResult.Achieved(gpa = rounded)
    }

    private fun roundTo2(v: Double): Double {
        // 用 BigDecimal 风格：四舍五入到 0.01
        return Math.round(v * 100.0) / 100.0
    }
}

/**
 * GPA 反推结果
 *
 *  - [Achieved] 目标可达，给出所需平均绩点
 *  - [Impossible] 超过学校最高绩点（规则 4）
 *  - [Trivial] 低于学校最低绩点（规则 5）
 *  - [NoFutureCredits] F = 0（规则 3）
 *  - [InvalidInput] 输入非法（负数 / 超出规则）
 */
sealed class GpaReverseResult {
    /** 目标可达 */
    data class Achieved(val gpa: Double) : GpaReverseResult()

    /** 在给定学分内目标不可达（已超过最高绩点） */
    data class Impossible(
        val requiredGpa: Double,
        val maxAchievable: Double,
        val scaleMax: Double,
    ) : GpaReverseResult()

    /** 目标在给定学分内很容易达到（所需 GF 低于最低绩点） */
    data class Trivial(
        val requiredGpa: Double,
        val minRequired: Double,
        val scaleMin: Double,
    ) : GpaReverseResult()

    /** 未来纳入绩点学分为 0 */
    data object NoFutureCredits : GpaReverseResult()

    /** 输入非法（负数、超过 scaleMax 等） */
    data class InvalidInput(val reason: String = "输入数据非法") : GpaReverseResult()
}
