package com.icgroup.icaagents.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandGreen = Color(0xFF0B3D2E)
val BrandGold = Color(0xFFC9A227)
val BrandOrange = Color(0xFFE06C1F)
val Surface = Color(0xFFFFFFFF)
val OnSurface = Color(0xFF1A1A1A)

private val LightColors = androidx.compose.material3.lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    secondary = BrandGold,
    onSecondary = Color.Black,
    tertiary = BrandOrange,
    background = Surface,
    surface = Surface,
    onBackground = OnSurface,
    onSurface = OnSurface
)

@Composable
fun IcaAgentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
