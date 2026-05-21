package com.darsma.glassgallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary              = Color(0xFFCFB8FF),
    onPrimary            = Color(0xFF330066),
    primaryContainer     = Color(0xFF4C0099),
    onPrimaryContainer   = Color(0xFFECDCFF),
    secondary            = Color(0xFFCBBFDA),
    onSecondary          = Color(0xFF332B42),
    secondaryContainer   = Color(0xFF4A4159),
    onSecondaryContainer = Color(0xFFE7DBF7),
    surface              = Color(0xFF080810),
    onSurface            = Color(0xFFE8E0F0),
    surfaceVariant       = Color(0xFF14121E),
    onSurfaceVariant     = Color(0xFFCCC3DC),
    background           = Color(0xFF080810),
    onBackground         = Color(0xFFE8E0F0),
    outline              = Color(0xFF978DA6),
)

@Composable
fun GlassGalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content     = content,
    )
}
