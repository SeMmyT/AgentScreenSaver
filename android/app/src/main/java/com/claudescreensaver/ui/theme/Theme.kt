package com.claudescreensaver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.claudescreensaver.data.models.Skin

@Composable
fun ClaudeScreenSaverTheme(
    skin: Skin = Skin.DEFAULT,
    content: @Composable () -> Unit,
) {
    val skinColors = SkinColors.from(skin.palette)

    val colorScheme = darkColorScheme(
        primary = skinColors.accent,
        secondary = skinColors.accentDeep,
        background = skinColors.background,
        surface = skinColors.background,
        onPrimary = skinColors.textPrimary,
        onSecondary = skinColors.textPrimary,
        onBackground = skinColors.textPrimary,
        onSurface = skinColors.textPrimary,
        outline = skinColors.textSecondary,
    )

    CompositionLocalProvider(LocalSkinColors provides skinColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ClaudeTypography,
            content = content,
        )
    }
}
