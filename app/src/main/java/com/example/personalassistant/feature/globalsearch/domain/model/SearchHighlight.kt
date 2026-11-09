package com.example.personalassistant.feature.globalsearch.domain.model

/**
 * Phase 22.3：搜索结果里的关键字高亮区间（纯函数，不碰 UI）
 *
 * ## 为什么放在 domain 层
 *
 * 「哪些片段该高亮」是**数据问题**，不是渲染问题。
 * 把它做成纯函数后可以脱离 Compose 单测 —— Compose 的 `AnnotatedString`
 * 在 JVM 单元测试里跑起来比纯 Kotlin 麻烦得多。
 *
 * 渲染（`AnnotatedString` + `SpanStyle`）在 UI 层做，见 `HighlightedText`。
 */
object SearchHighlight {

    /**
     * 找出 [text] 里所有匹配 [query] 的区间。
     *
     * - 大小写不敏感（用户搜「milk」应命中「Milk」）
     * - [query] 为空 → 没有高亮（不是「全文高亮」）
     * - 返回的区间按起点升序，**互不重叠**
     * - 匹配区间用 [CharSequence] 的 code unit 索引（Compose 的 `AnnotatedString` 也用这个）
     */
    fun ranges(text: String, query: String): List<IntRange> {
        val needle = query.trim()
        if (needle.isEmpty() || text.isEmpty()) return emptyList()

        val haystack = text.lowercase()
        val lowerNeedle = needle.lowercase()
        val result = mutableListOf<IntRange>()
        var from = 0

        while (from <= haystack.length - lowerNeedle.length) {
            val hit = haystack.indexOf(lowerNeedle, from)
            if (hit < 0) break
            val end = hit + lowerNeedle.length - 1
            result += hit..end
            // 从 end + 1 继续 —— 避免重叠匹配（"aaa" 搜 "aa" 只高亮第一处）
            from = end + 1
        }
        return result
    }

    /**
     * 判断 [text] 里有没有 [query] 的匹配（不关心位置）。
     */
    fun contains(text: String, query: String): Boolean =
        ranges(text, query).isNotEmpty()
}
