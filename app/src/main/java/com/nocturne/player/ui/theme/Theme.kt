package com.nocturne.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NocturneColorScheme = darkColorScheme(
    primary = NocturneColors.BrightStart,
    onPrimary = NocturneColors.TextPrimary,
    primaryContainer = NocturneColors.GradientStart,
    onPrimaryContainer = NocturneColors.TextPrimary,
    secondary = NocturneColors.BrightEnd,
    onSecondary = NocturneColors.TextPrimary,
    tertiary = NocturneColors.Amber,
    onTertiary = NocturneColors.Background,
    background = NocturneColors.Background,
    onBackground = NocturneColors.TextPrimary,
    surface = NocturneColors.Background,
    onSurface = NocturneColors.TextPrimary,
    surfaceVariant = NocturneColors.Surface,
    onSurfaceVariant = NocturneColors.TextSecondary,
    surfaceContainerLowest = NocturneColors.Background,
    surfaceContainerLow = NocturneColors.Surface,
    surfaceContainer = NocturneColors.Surface,
    surfaceContainerHigh = NocturneColors.SurfaceElevated,
    surfaceContainerHighest = NocturneColors.SurfaceElevated,
    inverseSurface = NocturneColors.TextPrimary,
    inverseOnSurface = NocturneColors.Background,
    outline = NocturneColors.ProgressTrack,
    outlineVariant = NocturneColors.ProgressTrack,
    error = NocturneColors.Error,
    onError = NocturneColors.Background,
    scrim = NocturneColors.Background,
)

/** Single app theme: colors, Manrope typography and the design radii. */
@Composable
fun NocturneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NocturneColorScheme,
        typography = NocturneTypography,
        shapes = NocturneMaterialShapes,
        content = content,
    )
}
