package com.example.facefit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = FaceFitTeal,
    secondary = FaceFitTealVariant,
    tertiary = Pink40,
    background = FaceFitBackground,
    surface = FaceFitSurface,
    onPrimary = Color.White,
    onSecondary = FaceFitTeal,
    onBackground = FaceFitTextPrimary,
    onSurface = FaceFitTextPrimary,
)

/** FaceFit uses its fixed brand palette regardless of device dark mode or wallpaper. */
@Composable
fun FacefitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
