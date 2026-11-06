package com.aura.browser.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AuraDarkColorScheme = darkColorScheme(
    primary = AuraColors.PrimaryAccent,
    onPrimary = AuraColors.TextPrimary,
    primaryContainer = AuraColors.GlassSurfaceElevated,
    onPrimaryContainer = AuraColors.TextPrimary,
    secondary = AuraColors.SecondaryCyan,
    onSecondary = AuraColors.DeepBackground,
    background = AuraColors.DeepBackground,
    onBackground = AuraColors.TextPrimary,
    surface = AuraColors.GlassSurface,
    onSurface = AuraColors.TextPrimary,
    surfaceVariant = AuraColors.ElevatedBackground,
    onSurfaceVariant = AuraColors.TextSecondary,
    error = AuraColors.DangerRed,
    outline = AuraColors.GlassBorder,
)

val AuraShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val AuraTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = AuraColors.TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = AuraColors.TextPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = AuraColors.TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = AuraColors.TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = AuraColors.TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = AuraColors.TextSecondary,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = AuraColors.TextSecondary,
    ),
)

@Composable
fun AuraBrowserTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AuraDarkColorScheme,
        shapes = AuraShapes,
        typography = AuraTypography,
        content = content,
    )
}
