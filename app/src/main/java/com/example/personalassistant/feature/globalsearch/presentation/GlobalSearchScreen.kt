package com.example.personalassistant.feature.globalsearch.presentation

import androidx.compose.foundation.clickable
import com.example.personalassistant.core.ui.components.EmptyState
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.personalassistant.R
import com.example.personalassistant.core.ui.components.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchHit
import com.example.personalassistant.feature.globalsearch.domain.model.GlobalSearchHitType
import com.example.personalassistant.feature.globalsearch.domain.model.SearchHighlight
import com.example.personalassistant.feature.globalsearch.presentation.components.HighlightedText

/**
 * Phase 8 Sub-step 3：全局搜索页
 *
 * ## 布局
 * - 顶栏：标题「全局搜索」+ 返回按钮
 * - 搜索框：实时输入（VM 内置 250ms debounce）
 * - 命中分组：按 TASK / EVENT / TRANSACTION / COURSE 分组展开
 * - 空查询：显示「输入关键字开始搜索」提示
 *
 * @param onBack 返回上一页
 * @param onHitClick 点击命中项跳转对应详情页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onHitClick: (GlobalSearchHit) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text(stringResource(R.string.global_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.Close, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 搜索框
            SearchBox(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
            )

            // 命中分组
            //
            // 扁平化 = 拼接 4 个列表 + 按时间倒序排序（O(n log n)），
            // 只依赖 state.result —— 结果没变就不重算
            val flattenedHits = remember(state.result) { state.flattened() }
            when {
                !state.hasQuery -> EmptySearchState(isNoResult = false)
                !state.hasResults -> EmptySearchState(isNoResult = true)
                else -> HitList(
                    hits = flattenedHits,
                    query = state.query,
                    onHitClick = onHitClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("搜索任务 / 事件 / 流水 / 课程…") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Clear, contentDescription = "清空")
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptySearchState(isNoResult: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            illustration = R.drawable.ic_empty_search,
            title = stringResource(
                if (isNoResult) R.string.empty_search_no_result_title
                else R.string.empty_search_title
            ),
            description = stringResource(
                if (isNoResult) R.string.empty_search_no_result_desc
                else R.string.empty_search_desc
            ),
        )
    }
}

@Composable
private fun HitList(
    hits: List<GlobalSearchHit>,
    query: String,
    onHitClick: (GlobalSearchHit) -> Unit,
) {
    // 按类型分组渲染：命中列表没变时分组结果也不变，缓存住
    val grouped = remember(hits) { hits.groupBy { it.type } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        GlobalSearchHitType.entries.forEach { type ->
            val items = grouped[type].orEmpty()
            if (items.isNotEmpty()) {
                item(key = "header_$type") {
                    SectionHeader(type = type, count = items.size)
                }
                items(items = items, key = { "${type}_${it.entityId}" }) { hit ->
                    HitCard(
                        hit = hit,
                        query = query,
                        onClick = { onHitClick(hit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(type: GlobalSearchHitType, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = type.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = type.label(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HitCard(
    hit: GlobalSearchHit,
    query: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = hit.type.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Phase 22.3：标题里的关键字高亮
                HighlightedText(
                    text = hit.title,
                    query = query,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!hit.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hit.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Phase 22.3：命中片段（关键字不在标题里时，它才有信息量）
            val highlight = hit.highlight
            if (!highlight.isNullOrBlank() && !SearchHighlight.contains(hit.title, query)) {
                Spacer(modifier = Modifier.height(2.dp))
                HighlightedText(
                    text = highlight,
                    query = query,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun GlobalSearchHitType.icon(): ImageVector = when (this) {
    GlobalSearchHitType.TASK -> Icons.Rounded.Checklist
    GlobalSearchHitType.EVENT -> Icons.Rounded.CalendarMonth
    GlobalSearchHitType.TRANSACTION -> Icons.Rounded.AccountBalanceWallet
    GlobalSearchHitType.COURSE -> Icons.Rounded.Today
}

private fun GlobalSearchHitType.label(): String = when (this) {
    GlobalSearchHitType.TASK -> "任务"
    GlobalSearchHitType.EVENT -> "事件"
    GlobalSearchHitType.TRANSACTION -> "流水"
    GlobalSearchHitType.COURSE -> "课程"
}