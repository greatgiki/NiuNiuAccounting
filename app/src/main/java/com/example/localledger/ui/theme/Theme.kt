package com.example.localledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.domain.model.TransactionType

val ExpenseColorLight = Color(0xFFC4392F)
val IncomeColorLight = Color(0xFF1E7D5A)
val TransferColorLight = Color(0xFF5C6B7A)
val ExpenseColorDark = Color(0xFFF08A80)
val IncomeColorDark = Color(0xFF7ED0AE)
val TransferColorDark = Color(0xFF9FB0BF)

val BrandGreen = Color(0xFF2E5D4B)
val BrandGold = Color(0xFFE0B15A)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E8DC),
    onPrimaryContainer = Color(0xFF0B2A1E),
    secondary = Color(0xFF4E6355),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D8),
    onSecondaryContainer = Color(0xFF0C1F14),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FA),
    onTertiaryContainer = Color(0xFF001F29),
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFF7F6F2),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDFE4DC),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val ChartColorsLight = listOf(
    Color(0xFFC4392F),
    Color(0xFFB45309),
    Color(0xFF1E7D5A),
    Color(0xFF1D4ED8),
    Color(0xFF6D28D9),
    Color(0xFF0F766E),
    Color(0xFFBE185D),
    Color(0xFF475569),
)

private val ChartColorsDark = listOf(
    Color(0xFFF08A80),
    Color(0xFFFBBF24),
    Color(0xFF7ED0AE),
    Color(0xFF93C5FD),
    Color(0xFFC4B5FD),
    Color(0xFF5EEAD4),
    Color(0xFFF9A8D4),
    Color(0xFFCBD5E1),
)

/** True when the app's [ThemeMode] resolves to a dark palette, including an explicit light or dark choice. */
val LocalLedgerDarkTheme = staticCompositionLocalOf { false }

fun amountColorFor(dark: Boolean, type: TransactionType): Color = when (type) {
    TransactionType.EXPENSE -> if (dark) ExpenseColorDark else ExpenseColorLight
    TransactionType.INCOME -> if (dark) IncomeColorDark else IncomeColorLight
    TransactionType.TRANSFER -> if (dark) TransferColorDark else TransferColorLight
}

fun chartPalette(dark: Boolean): List<Color> = if (dark) ChartColorsDark else ChartColorsLight

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9D3BF),
    onPrimary = Color(0xFF0B2A1E),
    primaryContainer = Color(0xFF2E5D4B),
    onPrimaryContainer = Color(0xFFD0E8DC),
    secondary = Color(0xFFB5CCBD),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3E),
    onSecondaryContainer = Color(0xFFD1E8D8),
    tertiary = Color(0xFFA5CCDF),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF254B5A),
    onTertiaryContainer = Color(0xFFC1E8FA),
    background = Color(0xFF121411),
    onBackground = Color(0xFFE2E3DE),
    surface = Color(0xFF121411),
    onSurface = Color(0xFFE2E3DE),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9BF),
    outline = Color(0xFF8B938A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Normal),
    displayMedium = TextStyle(fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.Normal),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    ),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun amountColor(type: TransactionType): Color = amountColorFor(LocalLedgerDarkTheme.current, type)

@Composable
fun LocalLedgerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalLedgerDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
