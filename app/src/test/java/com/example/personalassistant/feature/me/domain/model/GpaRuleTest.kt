package com.example.personalassistant.feature.me.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 12 GPA 规则模型测试
 *
 * 覆盖：默认转换表 / score→gradePoint / 边界值 / scaleMin 校验
 */
class GpaRuleTest {

    @Test
    fun `默认是 4_0 制`() {
        val r = GpaRule.DEFAULT
        assertEquals(4.0, r.scaleMax, 0.0)
        assertEquals(0.0, r.scaleMin, 0.0)
        assertEquals(4, r.gradingScale.size)
        assertEquals(GradeMapping(90, 4.0), r.gradingScale[0])
        assertEquals(GradeMapping(60, 1.0), r.gradingScale[3])
    }

    @Test
    fun `scoreToGradePoint - 高分区匹配`() {
        val r = GpaRule.DEFAULT
        assertEquals(4.0, r.scoreToGradePoint(95), 0.0)
        assertEquals(4.0, r.scoreToGradePoint(90), 0.0) // 边界 90
    }

    @Test
    fun `scoreToGradePoint - 中间档匹配`() {
        val r = GpaRule.DEFAULT
        assertEquals(3.0, r.scoreToGradePoint(85), 0.0)
        assertEquals(3.0, r.scoreToGradePoint(80), 0.0) // 边界 80
        assertEquals(2.0, r.scoreToGradePoint(75), 0.0)
        assertEquals(1.0, r.scoreToGradePoint(60), 0.0) // 边界 60
    }

    @Test
    fun `scoreToGradePoint - 低于最低段落落回 scaleMin`() {
        val r = GpaRule.DEFAULT
        assertEquals(0.0, r.scoreToGradePoint(59), 0.0)
        assertEquals(0.0, r.scoreToGradePoint(0), 0.0)
    }

    @Test
    fun `5_0 制 scaleMax=5`() {
        val r = GpaRule(scaleMax = 5.0, scaleMin = 0.0, gradingScale = listOf(
            GradeMapping(90, 5.0),
            GradeMapping(80, 4.0),
            GradeMapping(70, 3.0),
            GradeMapping(60, 2.0),
        ))
        assertEquals(5.0, r.scoreToGradePoint(95), 0.0)
        assertEquals(2.0, r.scoreToGradePoint(60), 0.0)
        assertEquals(0.0, r.scoreToGradePoint(50), 0.0)
    }

    @Test
    fun `scaleMin 必须 小于等于 scaleMax 否则抛异常`() {
        try {
            GpaRule(scaleMax = 3.0, scaleMin = 4.0)
            fail("应该抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("scaleMin"))
        }
    }

    @Test
    fun `scaleMax 必须 大于 0 否则抛异常`() {
        try {
            GpaRule(scaleMax = 0.0)
            fail("应该抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("scaleMax"))
        }
    }

    @Test
    fun `GradeMapping minScore 越界抛异常`() {
        try {
            GradeMapping(minScore = 101, gradePoint = 4.0)
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("minScore"))
        }
        try {
            GradeMapping(minScore = -1, gradePoint = 4.0)
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("minScore"))
        }
    }

    @Test
    fun `GradeMapping 绩点不能为负`() {
        try {
            GradeMapping(minScore = 80, gradePoint = -0.1)
            fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("gradePoint"))
        }
    }
}
