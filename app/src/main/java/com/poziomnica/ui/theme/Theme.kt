package com.poziomnica.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5EE28A),
    secondary = Color(0xFF87C9C2),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF0C1214),
    surface = Color(0xFF111A1D),
    surfaceVariant = Color(0xFF1C2A2D),
    onPrimary = Color(0xFF00391B),
    onBackground = Color(0xFFEAF2ED),
    onSurface = Color(0xFFEAF2ED),
    onSurfaceVariant = Color(0xFFB7C8C2)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF137A3A),
    secondary = Color(0xFF386A64),
    tertiary = Color(0xFF8A6500),
    background = Color(0xFFF7FAF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F5F1),
    outline = Color(0xFFD5DED7),
    outlineVariant = Color(0xFFE9EFEA),
    onPrimary = Color.White,
    onBackground = Color(0xFF17201B),
    onSurface = Color(0xFF17201B),
    onSurfaceVariant = Color(0xFF526059)
)

@Composable
fun PoziomnicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
