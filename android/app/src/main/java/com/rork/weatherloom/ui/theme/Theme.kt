package com.rork.weatherloom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LoomColorScheme = lightColorScheme(
    primary = Loom.Coral,
    onPrimary = Loom.Surface,
    primaryContainer = Loom.CoralSoft,
    onPrimaryContainer = Loom.Ink,
    secondary = Loom.Cold,
    onSecondary = Loom.Surface,
    secondaryContainer = Loom.ColdSoft,
    onSecondaryContainer = Loom.Ink,
    tertiary = Loom.Moisture,
    onTertiary = Loom.Surface,
    tertiaryContainer = Loom.MoistureSoft,
    onTertiaryContainer = Loom.Ink,
    background = Loom.Canvas,
    onBackground = Loom.Ink,
    surface = Loom.Canvas,
    onSurface = Loom.Ink,
    surfaceVariant = Loom.SurfaceSunk,
    onSurfaceVariant = Loom.Moss,
    surfaceContainer = Loom.Surface,
    surfaceContainerHigh = Loom.Surface,
    surfaceContainerHighest = Loom.Surface,
    surfaceContainerLow = Loom.Canvas,
    surfaceContainerLowest = Loom.Surface,
    outline = Loom.Outline,
    outlineVariant = Loom.Outline,
    error = Loom.Coral,
    onError = Loom.Surface
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoomColorScheme,
        typography = LoomTypography,
        content = content
    )
}
