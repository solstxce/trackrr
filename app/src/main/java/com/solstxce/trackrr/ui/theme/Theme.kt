package com.solstxce.trackrr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TrackrrDarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    surfaceContainer = Card,
    surfaceContainerHigh = Card,
    surfaceContainerHighest = SurfaceVariant,
    error = DangerRed,
    onError = OnBackground,
)

@Composable
fun TrackrrTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TrackrrDarkColorScheme,
        typography = Typography,
        content = content
    )
}