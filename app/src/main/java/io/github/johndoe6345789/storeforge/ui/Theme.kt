package io.github.johndoe6345789.storeforge.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF01875F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F1D6),
    onPrimaryContainer = Color(0xFF002114),
    secondaryContainer = Color(0xFFD2E8DC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD9B0),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF005237),
    onPrimaryContainer = Color(0xFFB7F1D6),
    secondaryContainer = Color(0xFF35493F),
)

@Composable
fun StoreForgeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
