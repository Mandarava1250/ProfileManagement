package com.example.personalassistant.feature.finance.presentation.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalassistant.feature.finance.domain.model.Category
import com.example.personalassistant.feature.finance.domain.model.Transaction
import com.example.personalassistant.feature.finance.domain.model.TransactionType
import com.example.personalassistant.feature.finance.presentation.components.CategoryChipRow

/**
 * Phase 7a：新增/编辑流水 BottomSheet
 *
 * 用法：
 * ```
 * AddTransactionSheet(
 *     onDismiss = { showSheet = false },
 *     editing = transactionForEdit,   // null = 新增模式
 * )
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    editing: Transaction? = null,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    LaunchedEffect(editing?.id) {
        if (editing != null) viewModel.loadForEdit(editing) else viewModel.reset()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (editing == null) "记一笔" else "编辑这笔",
                style = MaterialTheme.typography.titleLarge,
            )

            // 支出 / 收入 切换
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.type == TransactionType.EXPENSE,
                    onClick = { viewModel.setType(TransactionType.EXPENSE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("支出") }
                SegmentedButton(
                    selected = uiState.type == TransactionType.INCOME,
                    onClick = { viewModel.setType(TransactionType.INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("收入") }
            }

            // 金额输入
            OutlinedTextField(
                value = uiState.amountText,
                onValueChange = viewModel::setAmount,
                label = { Text("金额") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // 备注输入
            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::setNote,
                label = { Text("备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // 分类选择（按当前 type 过滤）
            Text(
                text = "分类",
                style = MaterialTheme.typography.titleMedium,
            )
            CategoryChipRow(
                categories = categories,
                selectedId = uiState.categoryId,
                onSelect = viewModel::setCategory,
                modifier = Modifier.fillMaxWidth(),
            )

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (editing != null) {
                    Button(
                        onClick = { viewModel.deleteCurrent(onDismiss) },
                        modifier = Modifier.weight(1f),
                    ) { Text("删除") }
                }
                Button(
                    onClick = { viewModel.save(onDismiss) },
                    enabled = uiState.canSave,
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
        }
    }
}
