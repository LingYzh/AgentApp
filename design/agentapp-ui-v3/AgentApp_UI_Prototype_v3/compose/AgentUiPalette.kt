package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * AgentApp UI v3 palette reference. Not compiled as an Android artifact here.
 * primary/actionContainer pair with WHITE onPrimary/onAction in both themes.
 * Use contentAccent for text-only/link accents (especially dark theme).
 * Adapt to the existing ColorScheme without changing persisted themeMode.
 */
data class AgentUiPalette(
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceHover: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outlineVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val brandSeed: Color,
    val success: Color,
    val successContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val info: Color,
    val codeBackground: Color,
    val contentAccent: Color,
    val actionContainer: Color,
    val onAction: Color,
    val errorActionContainer: Color,
    val onErrorAction: Color,
)

object AgentUiPalettes {
    val Light = AgentUiPalette(
        background = Color(0xFFFAF9F5),
        surface = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFF1EFE8),
        surfaceHover = Color(0xFFEAE7DE),
        onSurface = Color(0xFF262624),
        onSurfaceVariant = Color(0xFF6B6860),
        outlineVariant = Color(0xFFE4E1D7),
        primary = Color(0xFFA34F36),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF5E7DF),
        onPrimaryContainer = Color(0xFFA34F36),
        brandSeed = Color(0xFFD97757),
        success = Color(0xFF526447),
        successContainer = Color(0xFFEAF0E5),
        error = Color(0xFFA33732),
        errorContainer = Color(0xFFF7E7E4),
        info = Color(0xFF456882),
        codeBackground = Color(0xFFEEECE5),
        contentAccent = Color(0xFFA34F36),
        actionContainer = Color(0xFFA34F36),
        onAction = Color(0xFFFFFFFF),
        errorActionContainer = Color(0xFFA33732),
        onErrorAction = Color(0xFFFFFFFF),
    )

    val Dark = AgentUiPalette(
        background = Color(0xFF262624),
        surface = Color(0xFF30302D),
        surfaceContainer = Color(0xFF353530),
        surfaceHover = Color(0xFF41413A),
        onSurface = Color(0xFFF1F0E9),
        onSurfaceVariant = Color(0xFFB6B3AA),
        outlineVariant = Color(0xFF46463F),
        primary = Color(0xFFA9563D),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF49392F),
        onPrimaryContainer = Color(0xFFE6A086),
        brandSeed = Color(0xFFD97757),
        success = Color(0xFFABC399),
        successContainer = Color(0xFF333D2F),
        error = Color(0xFFECA29B),
        errorContainer = Color(0xFF493431),
        info = Color(0xFFA5C4DE),
        codeBackground = Color(0xFF20201E),
        contentAccent = Color(0xFFE6A086),
        actionContainer = Color(0xFFA9563D),
        onAction = Color(0xFFFFFFFF),
        errorActionContainer = Color(0xFFA33732),
        onErrorAction = Color(0xFFFFFFFF),
    )

    val ContextSegments = mapOf(
        "system" to Color(0xFF6B91CA),
        "tools" to Color(0xFF9C82C6),
        "environment" to Color(0xFF8995A5),
        "user" to Color(0xFF50A78F),
        "assistant" to Color(0xFFD3A05B),
        "results" to Color(0xFFCF7F8B),
        "attachments" to Color(0xFF5BA9BD),
        "summary" to Color(0xFFA0AF70),
    )
}
