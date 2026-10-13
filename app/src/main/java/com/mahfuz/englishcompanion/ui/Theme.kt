package com.mahfuz.englishcompanion.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object C {
    val Bg = Color(0xFF070D1F)
    val BgTop = Color(0xFF0C1736)
    val Card = Color(0xFF101A33)
    val CardHi = Color(0xFF16234A)
    val Border = Color(0xFF223463)
    val Blue = Color(0xFF2F6BFF)
    val BlueDeep = Color(0xFF1A3FBF)
    val Cyan = Color(0xFF4FC3F7)
    val Green = Color(0xFF2ECC71)
    val GreenBg = Color(0xFF0F3B2A)
    val Orange = Color(0xFFFF9F43)
    val Red = Color(0xFFFF5C5C)
    val Purple = Color(0xFF8E7CFF)
    val Text = Color(0xFFEAF0FF)
    val Muted = Color(0xFF8C9BC4)
    val UserBubble = Color(0xFF1B3A8C)

    val screen = Brush.verticalGradient(listOf(BgTop, Bg, Bg))
    val blueGlow = Brush.linearGradient(listOf(Blue, Color(0xFF5A8CFF)))
}

private val scheme = darkColorScheme(
    primary = C.Blue,
    onPrimary = Color.White,
    secondary = C.Cyan,
    background = C.Bg,
    surface = C.Card,
    onSurface = C.Text,
    onBackground = C.Text,
    surfaceVariant = C.CardHi,
    onSurfaceVariant = C.Muted,
    outline = C.Border,
    error = C.Red,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
