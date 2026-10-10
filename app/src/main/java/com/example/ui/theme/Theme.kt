package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val MekkaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

private fun buildLightScheme(accentPalette: String) = when (accentPalette.uppercase()) {
    "ROYAL_BLUE" -> lightColorScheme(
        primary = MekkaRoyalBluePrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDBEAFE),
        onPrimaryContainer = Color(0xFF1E3A8A),
        secondary = MekkaEmeraldPrimary,
        onSecondary = Color.White,
        secondaryContainer = MekkaEmeraldContainerLight,
        onSecondaryContainer = MekkaEmeraldOnContainerLight,
        tertiary = MekkaGoldPrimary,
        onTertiary = Color.White,
        background = MekkaLightBackground,
        onBackground = MekkaLightTextPrimary,
        surface = MekkaLightSurface,
        onSurface = MekkaLightTextPrimary,
        surfaceVariant = MekkaLightSurfaceElevated,
        onSurfaceVariant = MekkaLightTextSecondary,
        outline = MekkaLightOutline,
        error = FinancialNegative,
        onError = Color.White
    )
    "GOLD" -> lightColorScheme(
        primary = MekkaGoldPrimary,
        onPrimary = Color.White,
        primaryContainer = MekkaGoldContainerLight,
        onPrimaryContainer = MekkaGoldOnContainerLight,
        secondary = MekkaEmeraldPrimary,
        onSecondary = Color.White,
        secondaryContainer = MekkaEmeraldContainerLight,
        onSecondaryContainer = MekkaEmeraldOnContainerLight,
        tertiary = MekkaRoyalBluePrimary,
        onTertiary = Color.White,
        background = MekkaLightBackground,
        onBackground = MekkaLightTextPrimary,
        surface = MekkaLightSurface,
        onSurface = MekkaLightTextPrimary,
        surfaceVariant = MekkaLightSurfaceElevated,
        onSurfaceVariant = MekkaLightTextSecondary,
        outline = MekkaLightOutline,
        error = FinancialNegative,
        onError = Color.White
    )
    "SLATE" -> lightColorScheme(
        primary = MekkaSlatePrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE2E8F0),
        onPrimaryContainer = Color(0xFF0F172A),
        secondary = MekkaEmeraldPrimary,
        onSecondary = Color.White,
        secondaryContainer = MekkaEmeraldContainerLight,
        onSecondaryContainer = MekkaEmeraldOnContainerLight,
        tertiary = MekkaGoldPrimary,
        onTertiary = Color.White,
        background = MekkaLightBackground,
        onBackground = MekkaLightTextPrimary,
        surface = MekkaLightSurface,
        onSurface = MekkaLightTextPrimary,
        surfaceVariant = MekkaLightSurfaceElevated,
        onSurfaceVariant = MekkaLightTextSecondary,
        outline = MekkaLightOutline,
        error = FinancialNegative,
        onError = Color.White
    )
    else -> lightColorScheme( // EMERALD (Default Daylight-First POS Theme)
        primary = MekkaEmeraldPrimary,
        onPrimary = Color.White,
        primaryContainer = MekkaEmeraldContainerLight,
        onPrimaryContainer = MekkaEmeraldOnContainerLight,
        secondary = MekkaGoldPrimary,
        onSecondary = Color.White,
        secondaryContainer = MekkaGoldContainerLight,
        onSecondaryContainer = MekkaGoldOnContainerLight,
        tertiary = FinancialInfo,
        onTertiary = Color.White,
        background = MekkaLightBackground,
        onBackground = MekkaLightTextPrimary,
        surface = MekkaLightSurface,
        onSurface = MekkaLightTextPrimary,
        surfaceVariant = MekkaLightSurfaceElevated,
        onSurfaceVariant = MekkaLightTextSecondary,
        outline = MekkaLightOutline,
        error = FinancialNegative,
        onError = Color.White
    )
}

private fun buildDarkScheme(accentPalette: String) = when (accentPalette.uppercase()) {
    "ROYAL_BLUE" -> darkColorScheme(
        primary = Color(0xFF60A5FA),
        onPrimary = Color(0xFF0B111E),
        primaryContainer = Color(0xFF1E3A8A),
        onPrimaryContainer = Color(0xFFDBEAFE),
        secondary = Color(0xFF34D399),
        onSecondary = Color(0xFF064E3B),
        tertiary = Color(0xFFFBBF24),
        background = MekkaDarkBackground,
        onBackground = MekkaDarkTextPrimary,
        surface = MekkaDarkSurface,
        onSurface = MekkaDarkTextPrimary,
        surfaceVariant = MekkaDarkSurfaceElevated,
        onSurfaceVariant = MekkaDarkTextSecondary,
        outline = MekkaDarkOutline,
        error = Color(0xFFF87171)
    )
    "GOLD" -> darkColorScheme(
        primary = Color(0xFFFBBF24),
        onPrimary = Color(0xFF0B111E),
        primaryContainer = Color(0xFF78350F),
        onPrimaryContainer = Color(0xFFFEF3C7),
        secondary = Color(0xFF34D399),
        onSecondary = Color(0xFF064E3B),
        tertiary = Color(0xFF60A5FA),
        background = MekkaDarkBackground,
        onBackground = MekkaDarkTextPrimary,
        surface = MekkaDarkSurface,
        onSurface = MekkaDarkTextPrimary,
        surfaceVariant = MekkaDarkSurfaceElevated,
        onSurfaceVariant = MekkaDarkTextSecondary,
        outline = MekkaDarkOutline,
        error = Color(0xFFF87171)
    )
    else -> darkColorScheme(
        primary = Color(0xFF10B981),
        onPrimary = Color(0xFF052E16),
        primaryContainer = Color(0xFF064E3B),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFFF59E0B),
        onSecondary = Color(0xFF451A03),
        tertiary = Color(0xFF60A5FA),
        background = MekkaDarkBackground,
        onBackground = MekkaDarkTextPrimary,
        surface = MekkaDarkSurface,
        onSurface = MekkaDarkTextPrimary,
        surfaceVariant = MekkaDarkSurfaceElevated,
        onSurfaceVariant = MekkaDarkTextSecondary,
        outline = MekkaDarkOutline,
        error = Color(0xFFF87171)
    )
}

private fun scaleTypography(base: Typography, densityMode: String): Typography {
    val factor = when (densityMode.uppercase()) {
        "COMPACT" -> 0.92f
        "LARGE" -> 1.10f
        else -> 1.0f
    }
    if (factor == 1.0f) return base
    return Typography(
        displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * factor),
        headlineLarge = base.headlineLarge.copy(fontSize = base.headlineLarge.fontSize * factor),
        headlineMedium = base.headlineMedium.copy(fontSize = base.headlineMedium.fontSize * factor),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * factor),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * factor),
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * factor),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * factor),
        labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * factor),
        labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * factor)
    )
}

/**
 * Applies Arabic RTL layout direction and a Daylight-First Light Theme by default,
 * while supporting live customization of Theme Mode (LIGHT / DARK / SYSTEM),
 * Accent Palette (EMERALD / ROYAL_BLUE / GOLD / SLATE), and UI Density (COMPACT / COMFORTABLE / LARGE).
 */
@Composable
fun MyApplicationTheme(
    themeMode: String = "LIGHT",
    accentPalette: String = "EMERALD",
    uiDensity: String = "COMFORTABLE",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode.uppercase()) {
        "DARK" -> true
        "SYSTEM" -> systemDark
        else -> false // Default is crisp Daylight-First Light Mode
    }
    val colorScheme = if (useDark) {
        buildDarkScheme(accentPalette)
    } else {
        buildLightScheme(accentPalette)
    }
    val scaledTypography = scaleTypography(MekkaTypography, uiDensity)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            shapes = MekkaShapes,
            content = content
        )
    }
}
