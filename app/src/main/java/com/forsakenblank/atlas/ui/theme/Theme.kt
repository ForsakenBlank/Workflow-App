package com.forsakenblank.atlas.ui.theme

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
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.ThemeMode

private val Navy = Color(0xFF1F3A5F)
private val Teal = Color(0xFF2E8B80)
private val Mint = Color(0xFF7FD1C7)
private val Sand = Color(0xFFE9C46A)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3F7),
    onPrimaryContainer = Color(0xFF0B1D33),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEDE8),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8A6A12),
    tertiaryContainer = Color(0xFFFBE7B5),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFE1E6EE),
    surfaceContainer = Color(0xFFEDF1F6),
    surfaceContainerHigh = Color(0xFFE6EBF1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7EE),
    onPrimary = Color(0xFF0B1D33),
    primaryContainer = Color(0xFF2B4A72),
    onPrimaryContainer = Color(0xFFD5E3F7),
    secondary = Mint,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF1E4E48),
    onSecondaryContainer = Color(0xFFCDEDE8),
    tertiary = Sand,
    tertiaryContainer = Color(0xFF5C4708),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF2C333B),
    surfaceContainer = Color(0xFF1A2027),
    surfaceContainerHigh = Color(0xFF232A32),
)

@Composable
fun AtlasTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && settings.pureBlack) {
        colors = colors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0E0E0E),
            surfaceContainerHigh = Color(0xFF161616),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

// swatches offered when colouring folders, notes and trackers
val ItemColors = listOf(
    0xFFE57373, 0xFFFFB74D, 0xFFFFD54F, 0xFF81C784, 0xFF4DB6AC,
    0xFF64B5F6, 0xFF7986CB, 0xFFBA68C8, 0xFFF06292, 0xFF90A4AE,
).map { it.toInt() }
