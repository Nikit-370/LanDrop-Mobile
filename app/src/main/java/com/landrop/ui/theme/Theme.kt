package com.landrop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = SecondaryBlue,
    tertiary = AccentGreen,
    background = CosmicDarkBg,
    surface = CosmicSurface,
    onBackground = TextBody,
    onSurface = TextHeading,
    error = WarningRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // We default to dark theme for a premium cyber-slate feeling
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
