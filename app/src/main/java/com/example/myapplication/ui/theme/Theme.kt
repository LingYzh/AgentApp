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
    primary = Color(0xFFD97757),            // Anthropic 标志性陶土橙
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF7E7E0),   // 柔和淡陶土
    onPrimaryContainer = Color(0xFF4D1F10),
    secondary = Color(0xFF6A9BCC),          // Anthropic 官方次级蓝
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5EEF7),
    onSecondaryContainer = Color(0xFF1B3854),
    tertiary = Color(0xFF788C5D),           // Anthropic 官方鼠尾草绿
    tertiaryContainer = Color(0xFFE7EEDF),
    onTertiaryContainer = Color(0xFF263717),
    background = Color(0xFFFAF9F5),         // 官方 Ivory Cream 象牙暖米白
    onBackground = Color(0xFF141413),       // 官方 Slate Ink 板岩墨黑
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF141413),
    surfaceVariant = Color(0xFFE8E5DC),     // 官方 Warm Sand 暖沙气泡底色
    onSurfaceVariant = Color(0xFF6B665D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F2EA),// Claude 侧边栏与卡片微底色
    surfaceContainer = Color(0xFFEEEBE2),
    surfaceContainerHigh = Color(0xFFE7E3D8),
    surfaceContainerHighest = Color(0xFFE0DBD0),
    outline = Color(0xFFCCC7B8),
    outlineVariant = Color(0xFFE0DDD2),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val AnthropicDark = darkColorScheme(
    primary = Color(0xFFE08A6D),            // 亮陶土珊瑚橙
    onPrimary = Color(0xFF2B1107),
    primaryContainer = Color(0xFF4F2617),
    onPrimaryContainer = Color(0xFFFBE0D5),
    secondary = Color(0xFF8BB5DF),          // 通透次级蓝
    onSecondary = Color(0xFF142B40),
    secondaryContainer = Color(0xFF203B57),
    onSecondaryContainer = Color(0xFFD3E4F6),
    tertiary = Color(0xFF9CB27F),           // 通透次级绿
    tertiaryContainer = Color(0xFF334521),
    onTertiaryContainer = Color(0xFFE0EAD4),
    background = Color(0xFF141413),         // 官方 Slate Ink 暗夜深邃黑
    onBackground = Color(0xFFEDE8DF),       // 官方 Parchment 暖白字
    surface = Color(0xFF141413),
    onSurface = Color(0xFFEDE8DF),
    surfaceVariant = Color(0xFF282622),     // 深暖沙色卡片
    onSurfaceVariant = Color(0xFFA8A195),
    surfaceContainerLowest = Color(0xFF0F0F0E),
    surfaceContainerLow = Color(0xFF1B1A18),// Claude Dark 侧边栏与卡片微底色
    surfaceContainer = Color(0xFF22211E),
    surfaceContainerHigh = Color(0xFF2B2925),
    surfaceContainerHighest = Color(0xFF35332E),
    outline = Color(0xFF4A463F),
    outlineVariant = Color(0xFF33302A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AnthropicShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Material Design 3 Expressive (MD3e) 规范令牌
 */
object ExpressiveTokens {
    val DrawerWidth = 312.dp
    val DrawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    val PillShape = RoundedCornerShape(20.dp)
    val CardShape = RoundedCornerShape(18.dp)
    val HeaderCardShape = RoundedCornerShape(22.dp)
    val StatusBadgeShape = RoundedCornerShape(10.dp)
    val FabSafeBottomPadding = 88.dp
    val ScreenHorizontalPadding = 12.dp
}

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
