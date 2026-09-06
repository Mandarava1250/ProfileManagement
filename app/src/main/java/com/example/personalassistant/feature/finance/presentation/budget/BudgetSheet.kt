package com.example.personalassistant.feature.finance.presentation.budget

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Phase 7a：月度预算设置 Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSheet(
    onDismiss: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val persisted by viewModel.persistedBudget.collectAsStateWithLifecycle()

    LaunchedEffect(persisted) {
        viewModel.loadFromPersisted(persisted)
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
                text = "月度预算",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = editing.totalAmountText,
                onValueChange = viewModel::setAmount,
                label = { Text("本月可用金额") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("每天自动刷新零钱池", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = editing.isAutoRefresh,
                    onCheckedChange = viewModel::setAutoRefresh,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("昨日剩余结转今天", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = editing.carryOverEnabled,
                    onCheckedChange = viewModel::setCarryOverEnabled,
                )
            }

            Button(
                onClick = { viewModel.save(onDismiss) },
                enabled = editing.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存预算") }
        }
    }
}
