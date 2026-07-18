package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Anthropic 品牌风格：暖米白 + 陶土橙（浅）/ 暖炭黑 + 亮陶土（深）。
 */
private val AnthropicLight = lightColorScheme(
    primary = Color(0xFFD97757),            // 陶土橙
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6E4DC),
    onPrimaryContainer = Color(0xFF5C2E1B),
    secondary = Color(0xFF8A7B6F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE8E0),
    onSecondaryContainer = Color(0xFF3D362F),
    tertiary = Color(0xFF6B8E7B),
    tertiaryContainer = Color(0xFFE2EAE4),
    onTertiaryContainer = Color(0xFF2A4034),
    background = Color(0xFFFAF9F5),         // 暖米白
    onBackground = Color(0xFF1F1E1B),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF1F1E1B),
    surfaceVariant = Color(0xFFF0EEE6),     // 略深米色，卡片/气泡
    onSurfaceVariant = Color(0xFF5C564D),
    surfaceContainer = Color(0xFFF3F1EA),
    surfaceContainerHigh = Color(0xFFEDEAE1),
    outline = Color(0xFFB5AC9E),
    outlineVariant = Color(0xFFDCD6C9),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val AnthropicDark = darkColorScheme(
    primary = Color(0xFFE08A6D),            // 亮陶土橙
    onPrimary = Color(0xFF3D1A0C),
    primaryContainer = Color(0xFF6B3A26),
    onPrimaryContainer = Color(0xFFF8D9CB),
    secondary = Color(0xFFB0A08F),
    onSecondary = Color(0xFF2A241E),
    secondaryContainer = Color(0xFF3E382F),
    onSecondaryContainer = Color(0xFFD9CDC0),
    tertiary = Color(0xFF8FAF9B),
    tertiaryContainer = Color(0xFF33453B),
    onTertiaryContainer = Color(0xFFC4D8CB),
    background = Color(0xFF1F1E1B),         // 暖炭黑
    onBackground = Color(0xFFEAE6DE),
    surface = Color(0xFF1F1E1B),
    onSurface = Color(0xFFEAE6DE),
    surfaceVariant = Color(0xFF2C2A26),
    onSurfaceVariant = Color(0xFFB7B0A4),
    surfaceContainer = Color(0xFF262522),
    surfaceContainerHigh = Color(0xFF302E2A),
    outline = Color(0xFF6E675C),
    outlineVariant = Color(0xFF44403A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AnthropicShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun AgentTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) AnthropicDark else AnthropicLight,
        shapes = AnthropicShapes,
        content = content
    )
}
