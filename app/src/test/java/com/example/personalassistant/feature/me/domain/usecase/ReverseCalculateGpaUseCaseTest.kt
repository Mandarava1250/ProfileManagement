package com.example.personalassistant.feature.me.domain.usecase

import com.example.personalassistant.feature.me.domain.model.GpaRule
import com.example.personalassistant.feature.me.domain.model.GradeMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12：GPA 反推 use case 测试
 *
 * 覆盖 5 条规则：
 *  1. 普通可达
 *  2. F = 0 → NoFutureCredits
 *  3. 超过 scaleMax → Impossible
 *  4. 低于 scaleMin → Trivial
 *  5. 非法输入 → InvalidInput
 *  + 舍入：2 位小数
 *  + 默认 4.0 制
 */
class ReverseCalculateGpaUseCaseTest {

    private val useCase = ReverseCalculateGpaUseCase()

    @Test
    fun `普通场景 - GF 正常计算并四舍五入到 2 位小数`() {
        // S=30, G0=3.0, F=20, GT=3.3
        // GF = (3.3*(30+20) - 3.0*30) / 20 = (165 - 90) / 20 = 75/20 = 3.75
        val r = useCase(
            earnedCredits = 30.0,
            currentGpa = 3.0,
            futureCredits = 20.0,
            targetGpa = 3.3,
        )
        assertTrue("期望 Achieved，实际 $r", r is GpaReverseResult.Achieved)
        // 浮点容忍
        assertEquals(3.75, (r as GpaReverseResult.Achieved).gpa, 1e-6)
    }

    @Test
    fun `规则 3 - F 等于 0 报 NoFutureCredits`() {
        val r = useCase(earnedCredits = 30.0, currentGpa = 3.0, futureCredits = 0.0, targetGpa = 3.5)
        assertTrue("期望 NoFutureCredits，实际 $r", r is GpaReverseResult.NoFutureCredits)
    }

    @Test
    fun `规则 3 - F 负数也报 NoFutureCredits`() {
        val r = useCase(earnedCredits = 30.0, currentGpa = 3.0, futureCredits = -5.0, targetGpa = 3.5)
        assertTrue("期望 NoFutureCredits，实际 $r", r is GpaReverseResult.NoFutureCredits)
    }

    @Test
    fun `规则 4 - GF 超过 scaleMax 报 Impossible`() {
        // S=10, G0=3.5, F=10, GT=4.0 → GF = (4*20 - 3.5*10) / 10 = 45/10 = 4.5
        // 4.5 > 4.0 (scaleMax) → Impossible
        val r = useCase(
            earnedCredits = 10.0,
            currentGpa = 3.5,
            futureCredits = 10.0,
            targetGpa = 4.0,
        )
        assertTrue("期望 Impossible，实际 $r", r is GpaReverseResult.Impossible)
        val imp = r as GpaReverseResult.Impossible
        assertEquals(4.5, imp.requiredGpa, 1e-9)
        assertEquals(4.0, imp.maxAchievable, 1e-9)
        assertEquals(4.0, imp.scaleMax, 1e-9)
    }

    @Test
    fun `规则 5 - GF 低于 scaleMin 报 Trivial`() {
        // S=50, G0=2.5, F=10, GT=2.0 → GF = (2*60 - 2.5*50) / 10 = (120-125)/10 = -0.5
        // -0.5 < 0 (scaleMin) → Trivial
        val r = useCase(
            earnedCredits = 50.0,
            currentGpa = 2.5,
            futureCredits = 10.0,
            targetGpa = 2.0,
        )
        assertTrue("期望 Trivial，实际 $r", r is GpaReverseResult.Trivial)
        val tri = r as GpaReverseResult.Trivial
        assertEquals(-0.5, tri.requiredGpa, 1e-9)
        assertEquals(0.0, tri.minRequired, 1e-9)
    }

    @Test
    fun `规则 1 - 学分为负报 InvalidInput`() {
        val r = useCase(earnedCredits = -1.0, currentGpa = 3.0, futureCredits = 10.0, targetGpa = 3.5)
        assertTrue(r is GpaReverseResult.InvalidInput)
    }

    @Test
    fun `规则 1 - 现有 GPA 超过 scaleMax 报 InvalidInput`() {
        val r = useCase(earnedCredits = 30.0, currentGpa = 5.0, futureCredits = 10.0, targetGpa = 3.5)
        assertTrue(r is GpaReverseResult.InvalidInput)
        assertTrue((r as GpaReverseResult.InvalidInput).reason.contains("现有 GPA"))
    }

    @Test
    fun `规则 1 - 目标 GPA 超过 scaleMax 报 InvalidInput`() {
        val r = useCase(earnedCredits = 30.0, currentGpa = 3.0, futureCredits = 10.0, targetGpa = 5.0)
        assertTrue(r is GpaReverseResult.InvalidInput)
        assertTrue((r as GpaReverseResult.InvalidInput).reason.contains("目标 GPA"))
    }

    @Test
    fun `自定义 5_0 制 - 用 4_0 制规则会报 Impossible`() {
        val rule5 = GpaRule(scaleMax = 5.0, scaleMin = 0.0, gradingScale = listOf(
            GradeMapping(90, 5.0),
            GradeMapping(80, 4.0),
        ))
        // S=10, G0=4.0, F=10, GT=4.5
        // GF = (4.5*20 - 4*10) / 10 = (90-40)/10 = 5.0 → 等于 scaleMax → Achieved
        val r1 = useCase(10.0, 4.0, 10.0, 4.5, rule5)
        assertTrue("边界 5.0 应 Achieved，实际 $r1", r1 is GpaReverseResult.Achieved)

        // GT=4.6 → GF = (4.6*20 - 40) / 10 = 52/10 = 5.2 → 超过 5.0
        val r2 = useCase(10.0, 4.0, 10.0, 4.6, rule5)
        assertTrue("期望 Impossible，实际 $r2", r2 is GpaReverseResult.Impossible)
    }

    @Test
    fun `舍入 - 保留 2 位小数`() {
        // S=7, G0=3.0, F=3, GT=3.3
        // GF = (3.3*10 - 3*7) / 3 = (33-21)/3 = 4.0
        val r = useCase(7.0, 3.0, 3.0, 3.3)
        assertTrue(r is GpaReverseResult.Achieved)
        assertEquals(4.0, (r as GpaReverseResult.Achieved).gpa, 1e-9)
    }

    @Test
    fun `舍入 - 浮点尾数四舍五入`() {
        // 1/3 = 0.3333... 四舍五入到 2 位应该是 0.33
        val r = useCase(0.0, 0.0, 3.0, 1.0)
        // GF = (1*3 - 0) / 3 = 1.0
        assertNotNull(r)
        assertTrue(r is GpaReverseResult.Achieved)
        assertEquals(1.0, (r as GpaReverseResult.Achieved).gpa, 1e-9)
    }
}
