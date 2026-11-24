package com.example.ui.theme

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

private val RadarDarkColorScheme = darkColorScheme(
    primary = RadarCyanPrimary,
    onPrimary = Color(0xFF032238),
    primaryContainer = RadarBlueAccent,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = RadarCyanLight,
    onSecondary = Color(0xFF042137),
    background = RadarNavyDark,
    onBackground = TextPrimaryDark,
    surface = RadarCardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = RadarSurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = RadarBorderDark
)

private val RadarLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD),
    onPrimaryContainer = Color(0xFF032238),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    background = Color(0xFFF0F9FF),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE0F2FE),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun ChuvaRadarTheme(
    darkTheme: Boolean = true, // Tema escuro prioritário para visualização de radar
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> RadarDarkColorScheme
        else -> RadarLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    ChuvaRadarTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
