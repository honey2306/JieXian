package com.jiexian.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Forest = Color(0xFF173F35)
val ForestLight = Color(0xFF2D6253)
val Sage = Color(0xFFDAE6D5)
val SageLight = Color(0xFFECF1E7)
val Cream = Color(0xFFF6F5F0)
val Ink = Color(0xFF1D2522)
val Muted = Color(0xFF69736E)
val WarmWhite = Color(0xFFFFFFFF)
val Coral = Color(0xFFD7755B)

val NightBackground = Color(0xFF0A2420)
val NightSurface = Color(0xFF14342C)
val NightSurfaceElevated = Color(0xFF1A3B32)
val Moonlight = Color(0xFFDCE7CC)
val NightText = Color(0xFFEDF1E7)
val NightMuted = Color(0xFF9DB09F)

private val LightColors =
    lightColorScheme(
        primary = Forest,
        onPrimary = Color.White,
        primaryContainer = Sage,
        onPrimaryContainer = Forest,
        secondary = ForestLight,
        onSecondary = Color.White,
        secondaryContainer = SageLight,
        onSecondaryContainer = Forest,
        tertiary = Coral,
        background = Cream,
        onBackground = Ink,
        surface = WarmWhite,
        onSurface = Ink,
        surfaceVariant = SageLight,
        onSurfaceVariant = Muted,
        outline = Color(0xFFD7DCD5),
        error = Color(0xFFFFB4AB),
    )

private val NightColors =
    darkColorScheme(
        primary = Moonlight,
        onPrimary = Forest,
        primaryContainer = NightSurfaceElevated,
        onPrimaryContainer = Moonlight,
        secondary = Color(0xFFB7C9B3),
        onSecondary = Forest,
        secondaryContainer = NightSurface,
        onSecondaryContainer = NightText,
        tertiary = Color(0xFFE6B39E),
        background = NightBackground,
        onBackground = NightText,
        surface = NightSurface,
        onSurface = NightText,
        surfaceVariant = NightSurfaceElevated,
        onSurfaceVariant = NightMuted,
        outline = Color(0xFF355247),
        error = Color(0xFFFFB4AB),
    )

@Composable
fun JieXianTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) NightColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
