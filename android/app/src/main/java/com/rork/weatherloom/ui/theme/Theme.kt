package com.rork.weatherloom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LoomColorScheme = lightColorScheme(
    primary = Loom.CoralStrong,
    onPrimary = Loom.Surface,
    primaryContainer = Loom.CoralSoft,
    onPrimaryContainer = Loom.Ink,
    secondary = Loom.ColdStrong,
    onSecondary = Loom.Surface,
    secondaryContainer = Loom.ColdSoft,
    onSecondaryContainer = Loom.Ink,
    tertiary = Loom.MoistureStrong,
    onTertiary = Loom.Surface,
    tertiaryContainer = Loom.MoistureSoft,
    onTertiaryContainer = Loom.Ink,
    background = Loom.Canvas,
    onBackground = Loom.Ink,
    surface = Loom.Canvas,
    onSurface = Loom.Ink,
    surfaceVariant = Loom.SurfaceSunk,
    onSurfaceVariant = Loom.TextMuted,
    surfaceContainer = Loom.Surface,
    surfaceContainerHigh = Loom.Surface,
    surfaceContainerHighest = Loom.Surface,
    surfaceContainerLow = Loom.Canvas,
    surfaceContainerLowest = Loom.Surface,
    outline = Loom.Outline,
    outlineVariant = Loom.Outline,
    error = Loom.CoralStrong,
    onError = Loom.Surface
)

private val HighContrastLoomColorScheme = lightColorScheme(
    primary = Loom.Ink,
    onPrimary = Loom.Surface,
    primaryContainer = Loom.CoralSoft,
    onPrimaryContainer = Loom.Ink,
    secondary = Loom.TextCold,
    onSecondary = Loom.Surface,
    secondaryContainer = Loom.ColdSoft,
    onSecondaryContainer = Loom.Ink,
    tertiary = Loom.TextMoisture,
    onTertiary = Loom.Surface,
    tertiaryContainer = Loom.MoistureSoft,
    onTertiaryContainer = Loom.Ink,
    background = Loom.Canvas,
    onBackground = Loom.Ink,
    surface = Loom.Surface,
    onSurface = Loom.Ink,
    surfaceVariant = Loom.SurfaceSunk,
    onSurfaceVariant = Loom.Ink,
    surfaceContainer = Loom.Surface,
    surfaceContainerHigh = Loom.SurfaceSunk,
    surfaceContainerHighest = Loom.SurfaceSunk,
    surfaceContainerLow = Loom.Canvas,
    surfaceContainerLowest = Loom.Surface,
    outline = Loom.Ink,
    outlineVariant = Loom.TextMuted,
    error = Loom.TextAccent,
    onError = Loom.Surface
)

@Composable
fun AppTheme(highContrast: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (highContrast) HighContrastLoomColorScheme else LoomColorScheme,
        typography = LoomTypography,
        content = content
    )
}
