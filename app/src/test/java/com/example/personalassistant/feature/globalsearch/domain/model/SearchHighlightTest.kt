package com.example.personalassistant.feature.globalsearch.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 22.3：搜索关键字高亮区间
 */
class SearchHighlightTest {

    @Test
    fun `空关键字没有高亮`() {
        assertTrue(SearchHighlight.ranges("买牛奶", "").isEmpty())
        assertTrue(SearchHighlight.ranges("买牛奶", "   ").isEmpty())
    }

    @Test
    fun `空文本没有高亮`() {
        assertTrue(SearchHighlight.ranges("", "牛奶").isEmpty())
    }

    @Test
    fun `找不到时没有高亮`() {
        assertTrue(SearchHighlight.ranges("买牛奶", "面包").isEmpty())
        assertFalse(SearchHighlight.contains("买牛奶", "面包"))
    }

    @Test
    fun `命中开头`() {
        assertEquals(listOf(0..1), SearchHighlight.ranges("买牛奶", "买牛"))
    }

    @Test
    fun `命中中间`() {
        // "买牛奶" 里 "牛" 在索引 1
        assertEquals(listOf(1..1), SearchHighlight.ranges("买牛奶", "牛"))
    }

    @Test
    fun `命中结尾`() {
        // "买牛奶" 里 "奶" 在索引 2，单字命中 → 范围 2..2
        assertEquals(listOf(2..2), SearchHighlight.ranges("买牛奶", "奶"))
    }

    @Test
    fun `大小写不敏感`() {
        assertEquals(listOf(0..3), SearchHighlight.ranges("Milk Tea", "milk"))
        assertEquals(listOf(5..7), SearchHighlight.ranges("Milk Tea", "tea"))
    }

    @Test
    fun `多处命中全部返回`() {
        assertEquals(
            listOf(0..0, 4..4),
            SearchHighlight.ranges("a b a", "a"),
        )
    }

    @Test
    fun `重叠匹配不重复计数`() {
        // "aaa" 搜 "aa" → 只高亮第一处（区间 0..1），不产生重叠的第二处
        assertEquals(listOf(0..1), SearchHighlight.ranges("aaa", "aa"))
    }

    @Test
    fun `区间按起点升序`() {
        val ranges = SearchHighlight.ranges("x y x y x", "x")
        assertEquals(listOf(0..0, 4..4, 8..8), ranges)
    }

    @Test
    fun `关键字首尾空格被忽略`() {
        assertEquals(listOf(1..1), SearchHighlight.ranges("买牛奶", " 牛 "))
    }

    @Test
    fun `contains 与 ranges 一致`() {
        assertTrue(SearchHighlight.contains("买牛奶", "牛"))
        assertFalse(SearchHighlight.contains("买牛奶", "面包"))
    }
}
