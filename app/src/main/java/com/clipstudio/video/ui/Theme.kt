package com.clipstudio.video.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AccentAqua = Color(0xFF00D4E6)
val AccentBlue = Color(0xFF1D9BF0)

private val AmoledVideoColors = darkColorScheme(
    primary = AccentAqua,
    onPrimary = Color(0xFF04262B),
    primaryContainer = Color(0xFF0A2E36),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = AccentBlue,
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF05070C),
    onBackground = Color(0xFFF3F6FA),
    surface = Color(0xFF0C1018),
    onSurface = Color(0xFFF3F6FA),
    surfaceVariant = Color(0xFF151C28),
    onSurfaceVariant = Color(0xFF9AA7B8),
    outline = Color(0xFF232E40),
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun ClipStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledVideoColors,
        content = content
    )
}
