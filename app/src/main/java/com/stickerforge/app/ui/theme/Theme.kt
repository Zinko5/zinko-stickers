package com.stickerforge.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = SurfaceDark,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = TextPrimary,
    secondary = AccentTeal,
    onSecondary = SurfaceDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = TextPrimary,
    primaryContainer = EmeraldPrimary,
    onPrimaryContainer = SurfaceDark,
    secondary = AccentTeal,
    onSecondary = SurfaceDark,
    background = TextPrimary,
    onBackground = SurfaceDark,
    surface = TextPrimary,
    onSurface = SurfaceDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed
)

@Composable
fun StickerForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // Sleek dark mode by default

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
