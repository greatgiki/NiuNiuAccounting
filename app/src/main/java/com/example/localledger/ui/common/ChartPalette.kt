package com.example.localledger.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.theme.amountColor

object ChartPalette {
    val colors: List<Color> = listOf(
        Color(0xFFC4392F),
        Color(0xFFD97706),
        Color(0xFF2F6F4E),
        Color(0xFF2563EB),
        Color(0xFF7C3AED),
        Color(0xFF0F766E),
        Color(0xFFBE185D),
        Color(0xFF64748B),
    )

    fun color(index: Int): Color = colors[index % colors.size]
}

@Composable
fun categoryAccent(iconKey: String?, type: TransactionType): Color {
    if (type == TransactionType.TRANSFER || iconKey.isNullOrBlank()) return amountColor(type)
    val index = kotlin.math.abs(iconKey.hashCode()) % ChartPalette.colors.size
    return ChartPalette.color(index)
}
