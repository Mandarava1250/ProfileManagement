package com.example.personalassistant.feature.task.presentation.list

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 24：[ReorderHitResolver] 单测 —— 钉死「拖过半行就换位」这条规则。
 *
 * 老实现（Phase 6a）把「项自身高度的一半」当成了视口坐标，
 * 命中判定退化成「位移超过一整行高度才换位」。
 * 这组用例就是为了防止那条规则再被改坏。
 */
class ReorderHitResolverTest {

    @Test
    fun `位移没过半行不算越过`() {
        // 行高 100 → 半行 50；位移 30 还没过半
        assertFalse(ReorderHitResolver.shouldSwap(dragOffsetY = 30f, targetHeight = 100))
    }

    @Test
    fun `位移刚好过半行算越过`() {
        // 行高 100 → 半行 50；位移 51 过了
        assertTrue(ReorderHitResolver.shouldSwap(dragOffsetY = 51f, targetHeight = 100))
    }

    @Test
    fun `向上拖同样按绝对值判定`() {
        assertFalse(ReorderHitResolver.shouldSwap(dragOffsetY = -30f, targetHeight = 100))
        assertTrue(ReorderHitResolver.shouldSwap(dragOffsetY = -51f, targetHeight = 100))
    }

    @Test
    fun `位移恰好等于半行不算越过（要真的越过去）`() {
        // 半行 = 50，位移 = 50 —— 严格大于才算越过
        assertFalse(ReorderHitResolver.shouldSwap(dragOffsetY = 50f, targetHeight = 100))
    }

    @Test
    fun `行高未知（0）时一律不换位`() {
        // 目标行还没被布局出来（visibleItemsInfo 里查不到）→ 不能瞎换
        assertFalse(ReorderHitResolver.shouldSwap(dragOffsetY = 999f, targetHeight = 0))
    }

    @Test
    fun `行高越大越难越过（半行高随行高走）`() {
        // 行高 200 → 半行 100；位移 80 对 200 行的行来说没过半，对 100 行的行来说过了
        assertFalse(ReorderHitResolver.shouldSwap(dragOffsetY = 80f, targetHeight = 200))
        assertTrue(ReorderHitResolver.shouldSwap(dragOffsetY = 80f, targetHeight = 100))
    }
}
