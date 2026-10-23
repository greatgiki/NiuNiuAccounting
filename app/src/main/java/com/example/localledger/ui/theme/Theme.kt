package com.example.localledger.ui.theme

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localledger.core.storage.ThemeMode
import com.example.localledger.domain.model.TransactionType
import com.example.localledger.ui.component.rememberSettleOverscrollFactory

/** Muted terracotta. Chinese convention: expense reads as red, kept quiet enough for body text. */
val ExpenseColorLight = Color(0xFF8E3D32)
val IncomeColorLight = Color(0xFF2F6B52)
val TransferColorLight = Color(0xFF6A6258)
val ExpenseColorDark = Color(0xFFE0A598)
val IncomeColorDark = Color(0xFF8FBFAB)
val TransferColorDark = Color(0xFFC4BBAE)

/** Champagne brass dark enough for text and buttons on ivory (WCAG AA). */
val BrandGold = Color(0xFF6E5128)
val BrandGoldSoft = Color(0xFFD4B483)

private val Ivory = Color(0xFFF4EFE6)
private val Porcelain = Color(0xFFFBF8F3)
private val Ink = Color(0xFF1C1915)
private val InkSoft = Color(0xFF5C564C)
private val Graphite = Color(0xFF101216)
private val GraphiteRaised = Color(0xFF171A1F)

private val LightColors = lightColorScheme(
    primary = BrandGold,
    onPrimary = Color(0xFFFFF9F2),
    primaryContainer = Color(0xFFE7D7BC),
    onPrimaryContainer = Color(0xFF3A2C14),
    secondary = Color(0xFF5E6A62),
    onSecondary = Color(0xFFFFF9F2),
    secondaryContainer = Color(0xFFE3E6E1),
    onSecondaryContainer = Color(0xFF1C2420),
    tertiary = Color(0xFF6E5A3E),
    onTertiary = Color(0xFFFFF9F2),
    tertiaryContainer = Color(0xFFEADFCF),
    onTertiaryContainer = Color(0xFF2C2216),
    background = Ivory,
    onBackground = Ink,
    surface = Porcelain,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEBE4D8),
    onSurfaceVariant = InkSoft,
    surfaceTint = BrandGold,
    inverseSurface = Ink,
    inverseOnSurface = Ivory,
    inversePrimary = BrandGoldSoft,
    outline = Color(0xFFB7AFA2),
    outlineVariant = Color(0xFFD9D0C3),
    error = ExpenseColorLight,
    onError = Color(0xFFFFF8F6),
    errorContainer = Color(0xFFF3D9D3),
    onErrorContainer = Color(0xFF5C241C),
    surfaceContainerLowest = Color(0xFFFFFCF8),
    surfaceContainerLow = Color(0xFFF7F2EA),
    surfaceContainer = Color(0xFFF1EBE1),
    surfaceContainerHigh = Color(0xFFEBE4D8),
    surfaceContainerHighest = Color(0xFFE4DCCE),
)

private val DarkColors = darkColorScheme(
    primary = BrandGoldSoft,
    onPrimary = Color(0xFF2A2114),
    primaryContainer = Color(0xFF3D3426),
    onPrimaryContainer = Color(0xFFF3E6D0),
    secondary = Color(0xFFB7C4BC),
    onSecondary = Color(0xFF22302A),
    secondaryContainer = Color(0xFF2A3330),
    onSecondaryContainer = Color(0xFFD7E3DC),
    tertiary = Color(0xFFD7C4A4),
    onTertiary = Color(0xFF2C2418),
    tertiaryContainer = Color(0xFF3A3228),
    onTertiaryContainer = Color(0xFFF0E4D0),
    background = Graphite,
    onBackground = Color(0xFFEDE6DA),
    surface = GraphiteRaised,
    onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF2C313A),
    onSurfaceVariant = Color(0xFFC4BBAE),
    surfaceTint = BrandGoldSoft,
    inverseSurface = Ivory,
    inverseOnSurface = Ink,
    inversePrimary = BrandGold,
    outline = Color(0xFF8A8278),
    outlineVariant = Color(0xFF3A3F47),
    error = ExpenseColorDark,
    onError = Color(0xFF4A241E),
    errorContainer = Color(0xFF5C332C),
    onErrorContainer = Color(0xFFF8E4DE),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF1C2026),
    surfaceContainer = Color(0xFF232830),
    surfaceContainerHigh = Color(0xFF2C313A),
    surfaceContainerHighest = Color(0xFF373D48),
)

private val ChartColorsLight = listOf(
    ExpenseColorLight,
    BrandGold,
    IncomeColorLight,
    Color(0xFF4E6270),
    Color(0xFF6A5A78),
    Color(0xFF3F6F66),
    Color(0xFF8A5A62),
    TransferColorLight,
)

private val ChartColorsDark = listOf(
    ExpenseColorDark,
    BrandGoldSoft,
    IncomeColorDark,
    Color(0xFF9BB0C0),
    Color(0xFFC4B4D4),
    Color(0xFF8FBFB6),
    Color(0xFFE4C0C6),
    TransferColorDark,
)

/** True when the app's [ThemeMode] resolves to a dark palette, including an explicit light or dark choice. */
val LocalLedgerDarkTheme = staticCompositionLocalOf { false }

fun amountColorFor(dark: Boolean, type: TransactionType): Color = when (type) {
    TransactionType.EXPENSE -> if (dark) ExpenseColorDark else ExpenseColorLight
    TransactionType.INCOME -> if (dark) IncomeColorDark else IncomeColorLight
    TransactionType.TRANSFER -> if (dark) TransferColorDark else TransferColorLight
}

fun chartPalette(dark: Boolean): List<Color> = if (dark) ChartColorsDark else ChartColorsLight

private val numeralLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 52.sp,
        lineHeight = 58.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = "tnum",
        lineHeightStyle = numeralLineHeight,
    ),
    displayMedium = TextStyle(
        fontSize = 42.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.6).sp,
        fontFeatureSettings = "tnum",
        lineHeightStyle = numeralLineHeight,
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum",
        lineHeightStyle = numeralLineHeight,
    ),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Immutable
data class LedgerTones(
    val heroTop: Color,
    val heroBottom: Color,
    val heroInk: Color,
    val heroMuted: Color,
    val hairline: Color,
    val accent: Color,
)

val LocalLedgerTones = staticCompositionLocalOf {
    LedgerTones(
        heroTop = Color(0xFFF3EADF),
        heroBottom = Color(0xFFE7DDD0),
        heroInk = Ink,
        heroMuted = InkSoft,
        hairline = Color(0xFFD9D0C3),
        accent = BrandGold,
    )
}

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
    val tones = if (dark) {
        LedgerTones(
            heroTop = Color(0xFF242830),
            heroBottom = Color(0xFF1A1D22),
            heroInk = Color(0xFFEDE6DA),
            heroMuted = Color(0xFFC4BBAE),
            hairline = Color(0xFF3A3F47),
            accent = BrandGoldSoft,
        )
    } else {
        LedgerTones(
            heroTop = Color(0xFFF3EADF),
            heroBottom = Color(0xFFE7DDD0),
            heroInk = Ink,
            heroMuted = InkSoft,
            hairline = Color(0xFFD9D0C3),
            accent = BrandGold,
        )
    }
    val overscroll = rememberSettleOverscrollFactory()
    CompositionLocalProvider(
        LocalLedgerDarkTheme provides dark,
        LocalLedgerTones provides tones,
        LocalOverscrollFactory provides overscroll,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
