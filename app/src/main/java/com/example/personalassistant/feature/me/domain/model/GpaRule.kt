package com.example.personalassistant.feature.me.domain.model

import kotlinx.serialization.Serializable

/**
 * 一条「百分制分数段 → 绩点」的映射
 *
 * 设计要点：
 *  - 列表按 minScore 降序排（高分区优先匹配）
 *  - 任何 minScore ≤ X 的分数匹配第一个 minScore ≤ X 的条目
 *  - 没有匹配的项，按 [GpaRule.scaleMin] 兜底
 *  - 默认值见 [GpaRule.DEFAULT_GRADING_SCALE]
 *
 * Phase 12 扩展
 */
@Serializable
data class GradeMapping(
    /** 分数段下限（含）。0~100 */
    val minScore: Int,
    /** 该段对应的绩点，0 ~ scaleMax */
    val gradePoint: Double,
) {
    init {
        require(minScore in 0..100) { "minScore 必须在 0..100，实际 $minScore" }
        require(gradePoint >= 0.0) { "gradePoint 不能为负，实际 $gradePoint" }
    }
}

/**
 * GPA 计算规则（学校定制）
 *
 * 默认 4.0 制：
 *  - [scaleMax] = 4.0
 *  - [scaleMin] = 0.0
 *  - [gradingScale] = 4 档（90/4.0, 80/3.0, 70/2.0, 60/1.0，<60 兜底 scaleMin）
 *
 * 自定义学校（如 5.0 制 / 4.3 制）只需替换三个字段，旧成绩按新规则重新计算。
 *
 * Phase 12 扩展
 */
@Serializable
data class GpaRule(
    /** 学校最高绩点（满分绩点）。中国/美国 4.0、加拿大 4.3、英国某些 5.0 */
    val scaleMax: Double = 4.0,
    /** 学校最低绩点（不及格线对应的绩点），通常 0.0。少数学校 P/F 课算 1.0 */
    val scaleMin: Double = 0.0,
    /** 百分制分数段 → 绩点。空时按 [DEFAULT_GRADING_SCALE] 兜底 */
    val gradingScale: List<GradeMapping> = DEFAULT_GRADING_SCALE,
) {
    init {
        require(scaleMax > 0.0) { "scaleMax 必须 > 0" }
        require(scaleMin in 0.0..scaleMax) { "scaleMin 必须在 [0, scaleMax]=$scaleMax，实际 $scaleMin" }
    }

    /**
     * 把百分制分数翻成绩点
     *
     * 规则：gradingScale 按 minScore 降序后，第一个 minScore ≤ score 的就是结果；
     * 没有匹配（score 低于最低档）时返回 [scaleMin]。
     */
    fun scoreToGradePoint(score: Int): Double {
        val sorted = gradingScale.sortedByDescending { it.minScore }
        return sorted.firstOrNull { score >= it.minScore }?.gradePoint ?: scaleMin
    }

    /** 把绩点翻成最近的百分制分数下界（用于反查 UI 显示） */
    fun gradePointToMinScore(gradePoint: Double): Int? =
        gradingScale.filter { it.gradePoint == gradePoint }.maxByOrNull { it.minScore }?.minScore

    companion object {
        /** 中国通用 4.0 制默认转换表 */
        val DEFAULT_GRADING_SCALE: List<GradeMapping> = listOf(
            GradeMapping(90, 4.0),
            GradeMapping(80, 3.0),
            GradeMapping(70, 2.0),
            GradeMapping(60, 1.0),
        )

        val DEFAULT = GpaRule()
    }
}
