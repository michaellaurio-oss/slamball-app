package com.laurio.slamlog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF6B2C)
val Mint = Color(0xFF3DDC97)
val Sky = Color(0xFF60A5FA)
val Amber = Color(0xFFFBBF24)

private val Scheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1A0E08),
    secondary = Mint,
    onSecondary = Color(0xFF06140E),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF181B21),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF22262E),
    onSurfaceVariant = Color(0xFFA7ACB5),
    secondaryContainer = Color(0xFF2A2F38),
    onSecondaryContainer = Color(0xFFECEDEF),
    error = Color(0xFFEF4444),
)

@Composable
fun SlamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
