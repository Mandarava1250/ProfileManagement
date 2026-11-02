package com.example.personalassistant.feature.globalsearch.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import com.example.personalassistant.feature.globalsearch.domain.model.SearchHighlight

/**
 * Phase 22.3：带关键字高亮的文本
 *
 * 用 `AnnotatedString` + `SpanStyle` 渲染，区间计算交给
 * [SearchHighlight]（domain 层纯函数，可 JVM 单测）。
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Text(
        text = buildHighlightedString(text, query),
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = color,
        style = style,
    )
}

/**
 * 把 [text] 里匹配 [query] 的片段包上高亮样式。
 *
 * 抽成独立函数是为了让它也能被 JVM 单测直接验证。
 */
fun buildHighlightedString(
    text: String,
    query: String,
    highlightColor: ComposeColor = ComposeColor(0xFFE91E63),
): AnnotatedString {
    val ranges = SearchHighlight.ranges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            if (range.first > cursor) {
                append(text.substring(cursor, range.first))
            }
            withStyle(
                SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(text.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
