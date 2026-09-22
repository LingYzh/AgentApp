package com.example.myapplication.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp
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
 * AgentApp V3 语义配色；实心按钮始终白色前景，不使用壁纸动态色。
 */
private val AnthropicLight = lightColorScheme(
    primary = Color(0xFFA34F36),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5E7DF),
    onPrimaryContainer = Color(0xFFA34F36),
    secondary = Color(0xFF6A9BCC),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5EEF7),
    onSecondaryContainer = Color(0xFF1B3854),
    tertiary = Color(0xFF788C5D),
    tertiaryContainer = Color(0xFFE7EEDF),
    onTertiaryContainer = Color(0xFF263717),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF141413),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF262624),
    surfaceVariant = Color(0xFFE8E5DC),
    onSurfaceVariant = Color(0xFF6B6860),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1EFE8),
    surfaceContainer = Color(0xFFF1EFE8),
    surfaceContainerHigh = Color(0xFFE7E3D8),
    surfaceContainerHighest = Color(0xFFE0DBD0),
    outline = Color(0xFFCCC7B8),
    outlineVariant = Color(0xFFE4E1D7),
    error = Color(0xFFA33732),
    errorContainer = Color(0xFFF7E7E4),
    onErrorContainer = Color(0xFF410002)
)

private val AnthropicDark = darkColorScheme(
    primary = Color(0xFFA9563D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF49392F),
    onPrimaryContainer = Color(0xFFE6A086),
    secondary = Color(0xFF8BB5DF),
    onSecondary = Color(0xFF142B40),
    secondaryContainer = Color(0xFF203B57),
    onSecondaryContainer = Color(0xFFD3E4F6),
    tertiary = Color(0xFF9CB27F),
    tertiaryContainer = Color(0xFF334521),
    onTertiaryContainer = Color(0xFFE0EAD4),
    background = Color(0xFF262624),
    onBackground = Color(0xFFEDE8DF),
    surface = Color(0xFF30302D),
    onSurface = Color(0xFFF1F0E9),
    surfaceVariant = Color(0xFF282622),
    onSurfaceVariant = Color(0xFFB6B3AA),
    surfaceContainerLowest = Color(0xFF0F0F0E),
    surfaceContainerLow = Color(0xFF30302D),
    surfaceContainer = Color(0xFF353530),
    surfaceContainerHigh = Color(0xFF2B2925),
    surfaceContainerHighest = Color(0xFF35332E),
    outline = Color(0xFF4A463F),
    outlineVariant = Color(0xFF46463F),
    error = Color(0xFFECA29B),
    errorContainer = Color(0xFF493431),
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
    val ScreenHorizontalPadding = 22.dp
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
    val target = if (dark) AnthropicDark else AnthropicLight
    val background by animateColorAsState(target.background, tween(180), label = "background")
    val surface by animateColorAsState(target.surface, tween(180), label = "surface")
    val foreground by animateColorAsState(target.onSurface, tween(180), label = "foreground")
    val primary by animateColorAsState(target.primary, tween(180), label = "action")
    val outline by animateColorAsState(target.outlineVariant, tween(180), label = "outline")
    MaterialTheme(
        colorScheme = target.copy(background = background, surface = surface, onSurface = foreground,
            onBackground = foreground, primary = primary, outlineVariant = outline,
            primaryContainer = animatedToken(target.primaryContainer), onPrimaryContainer = animatedToken(target.onPrimaryContainer),
            surfaceContainer = animatedToken(target.surfaceContainer), surfaceContainerLow = animatedToken(target.surfaceContainerLow),
            surfaceContainerHigh = animatedToken(target.surfaceContainerHigh), surfaceContainerHighest = animatedToken(target.surfaceContainerHighest),
            surfaceVariant = animatedToken(target.surfaceVariant), onSurfaceVariant = animatedToken(target.onSurfaceVariant),
            error = animatedToken(target.error), errorContainer = animatedToken(target.errorContainer)),
        typography = Typography().let { it.copy(bodyLarge = it.bodyLarge.copy(fontSize = 16.sp, lineHeight = 28.sp)) },
        shapes = AnthropicShapes,
        content = content
    )
}

@Composable
private fun animatedToken(target: Color): Color = animateColorAsState(target, tween(180), label = "theme token").value
