package de.nielstron.simplenextcloud.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00679E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E8F7),
    onPrimaryContainer = Color(0xFF07364D),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF0),
    outlineVariant = Color(0xFFD8DDE1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DCEF1),
    primaryContainer = Color(0xFF164C67),
    background = Color(0xFF101417),
    surface = Color(0xFF171C20),
    surfaceVariant = Color(0xFF252B2F),
)

@Composable
fun SimpleNextcloudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
