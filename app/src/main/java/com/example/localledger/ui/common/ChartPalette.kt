package com.example.localledger.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.theme.LocalLedgerDarkTheme
import com.example.localledger.ui.theme.amountColor
import com.example.localledger.ui.theme.chartPalette

object ChartPalette {
    val colors: List<Color> = listOf(
        Color(0xFF8E3D32),
        Color(0xFF6E5128),
        Color(0xFF2F6B52),
        Color(0xFF4E6270),
        Color(0xFF6A5A78),
        Color(0xFF3F6F66),
        Color(0xFF8A5A62),
        Color(0xFF6A6258),
    )

    fun color(index: Int): Color = colors[index % colors.size]
}

@Composable
fun categoryAccent(iconKey: String?, type: TransactionType): Color {
    if (type == TransactionType.TRANSFER || iconKey.isNullOrBlank()) return amountColor(type)
    val colors = chartPalette(LocalLedgerDarkTheme.current)
    val index = kotlin.math.abs(iconKey.hashCode()) % colors.size
    return colors[index]
}
