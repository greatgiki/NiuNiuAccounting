package com.example.localledger.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.localledger.domain.model.Account
import com.example.localledger.domain.model.Category
import com.example.localledger.domain.money.CurrencyCatalog
import com.example.localledger.ui.common.CategoryIcons

@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currencyCode: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = { Text(CurrencyCatalog.symbol(currencyCode)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDropdownField(
    label: String,
    accounts: List<Account>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.name ?: "请选择",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onSelect(account.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
fun CategoryIconGrid(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(categories) { categories.chunked(4) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { category ->
                    key(category.id) {
                    val icon = remember(category.iconKey) { CategoryIcons.resolve(category.iconKey).image }
                    FilterChip(
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) },
                        label = {
                            Text(
                                category.name,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
