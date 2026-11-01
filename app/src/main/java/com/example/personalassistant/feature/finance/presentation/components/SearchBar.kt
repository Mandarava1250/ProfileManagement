package com.example.personalassistant.feature.finance.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Phase 7b：流水搜索条
 *
 * - OutlinedTextField 输入关键字
 * - 右侧清除按钮（仅在 keyword 非空时显示）
 * - 父 Composable 通过 [value] / [onValueChange] 控制 state
 */
@Composable
fun SearchBar(
    keyword: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索备注或分类",
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onValueChange,
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = "搜索")
        },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "清除")
                }
            }
        },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}