package com.svensson.titan.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TitanDarkColors = darkColorScheme(
    primary = TitanPrimary,
    onPrimary = TitanOnPrimary,
    primaryContainer = TitanPrimaryContainer,
    onPrimaryContainer = TitanOnPrimaryContainer,
    secondary = TitanSecondary,
    onSecondary = TitanOnSecondary,
    secondaryContainer = TitanSecondaryContainer,
    onSecondaryContainer = TitanOnSecondaryContainer,
    tertiary = TitanTertiary,
    background = TitanBackground,
    onBackground = TitanOnBackground,
    surface = TitanSurface,
    onSurface = TitanOnSurface,
    surfaceVariant = TitanSurfaceVariant,
    onSurfaceVariant = TitanOnSurfaceVariant,
    outline = TitanOutline,
    error = TitanError,
    onError = TitanOnError,
)

@Composable
fun TitanTrainerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TitanDarkColors,
        typography = TitanTypography,
        shapes = TitanShapes,
        content = content,
    )
}