package com.claudescreensaver.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.claudescreensaver.data.models.Skin
import com.claudescreensaver.data.models.SkinPalette

/**
 * Skin-driven color palette for Compose UI.
 * Screens read from [LocalSkinColors] to get the active skin's palette.
 */
data class SkinColors(
    val accent: Color,
    val accentDeep: Color,
    val background: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
) {
    companion object {
        fun from(palette: SkinPalette) = SkinColors(
            accent = Color(palette.accent.toInt()),
            accentDeep = Color(palette.accentDeep.toInt()),
            background = Color(palette.background.toInt()),
            textPrimary = Color(palette.textPrimary.toInt()),
            textSecondary = Color(palette.textSecondary.toInt()),
            textTertiary = Color(palette.textTertiary.toInt()),
        )

        val Default = from(Skin.DEFAULT.palette)
    }
}

val LocalSkinColors = staticCompositionLocalOf { SkinColors.Default }

// Convenience aliases — drop-in replacements for the old Claude* constants.
// These read from the nearest CompositionLocal provider, so they pick up the active skin.
val ClaudeBgDark @Composable get() = LocalSkinColors.current.background
val ClaudeAccent @Composable get() = LocalSkinColors.current.accent
val ClaudeAccentDeep @Composable get() = LocalSkinColors.current.accentDeep
val ClaudeTextLight @Composable get() = LocalSkinColors.current.textPrimary
val ClaudeGray @Composable get() = LocalSkinColors.current.textSecondary
val ClaudeParchment @Composable get() = LocalSkinColors.current.textTertiary

// Status colors (Astro UX inspired) — not skin-driven
val StatusRunning = Color(0xFF56F000)
val StatusStandby = Color(0xFF2DCCFF)
val StatusCaution = Color(0xFFFCE83A)
val StatusWarning = Color(0xFFFFB302)
val StatusCritical = Color(0xFFFF3838)
val StatusDisabled = Color(0xFFA4ABB6)
