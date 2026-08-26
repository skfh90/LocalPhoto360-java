package com.localphoto360.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Night,
    secondary = GoldDim,
    onSecondary = Night,
    background = Night,
    onBackground = Cream,
    surface = NightElevated,
    onSurface = Cream,
    onSurfaceVariant = Muted,
    error = Danger,
    onError = Cream,
)

@Composable
fun LocalPhoto360Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content,
    )
}
