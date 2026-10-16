package com.example.localledger.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localledger.domain.money.CurrencyCatalog

@Composable
fun CurrencyChips(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        CurrencyCatalog.supported.forEach { spec ->
            FilterChip(
                selected = selected == spec.code,
                onClick = { onSelect(spec.code) },
                label = { Text("${spec.label} ${spec.code}") },
            )
            Spacer(Modifier.padding(2.dp))
        }
    }
}
