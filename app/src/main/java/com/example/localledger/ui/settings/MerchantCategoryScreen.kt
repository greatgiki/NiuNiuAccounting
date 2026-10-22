package com.example.localledger.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.localledger.data.repository.MerchantCategoryRule
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.component.EmptyState
import com.example.localledger.ui.component.LedgerCard
import com.example.localledger.ui.component.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantCategoryScreen(
    navController: NavHostController,
    viewModel: MerchantCategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("商家分类") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.rules.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearAll) { Text("全部清除") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !state.ready -> Box(Modifier.padding(padding)) { LoadingState() }
            state.rules.isEmpty() -> Box(Modifier.padding(padding)) {
                EmptyState("还没有记住商家对应的分类。在待确认、导入账单或流水里手动改过分类后，会记在这里，下次优先使用。")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                item(key = "intro", contentType = "intro") {
                    Text(
                        "同一商家再次出现在通知或账单里时，会先用这里的分类。",
                        modifier = Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.rules, key = { it.id }, contentType = { "merchant-rule" }) { rule ->
                    LedgerCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        RuleRow(rule, onClear = { viewModel.clear(rule.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: MerchantCategoryRule, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rule.merchantLabel, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${if (rule.type == TransactionType.INCOME) "收入" else "支出"} · ${rule.categoryName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClear, modifier = Modifier.heightIn(min = 48.dp)) { Text("清除") }
    }
}
