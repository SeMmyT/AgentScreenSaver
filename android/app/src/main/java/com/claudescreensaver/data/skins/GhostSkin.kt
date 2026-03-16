package com.claudescreensaver.data.skins

import com.claudescreensaver.data.models.*

/**
 * The default Ghost skin -- Claude Code's mascot.
 * "Each session is a dream. The koans make it lucid."
 *
 * Pixel grid legend:
 * 0 = transparent
 * 1 = body (ghost white/tinted)
 * 2 = accent (eyes, details)
 * 3 = eye pupil (dark)
 * 4 = highlight (lighter body)
 */
fun createGhostSkin(): Skin {
    // 12x12 ghost pixel art -- classic Pac-Man ghost silhouette
    // Rounded dome top, wide body, wavy tentacle bottom
    val grid = listOf(
        listOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0),  // row 0: dome top
        listOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0),  // row 1
        listOf(0, 1, 1, 4, 1, 1, 1, 1, 4, 1, 1, 0),  // row 2: highlights
        listOf(0, 1, 2, 3, 2, 1, 1, 2, 3, 2, 1, 0),  // row 3: eyes
        listOf(0, 1, 2, 3, 2, 1, 1, 2, 3, 2, 1, 0),  // row 4: eyes
        listOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),  // row 5: body
        listOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),  // row 6: body
        listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),  // row 7: widest
        listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),  // row 8
        listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),  // row 9
        listOf(1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1),  // row 10: wavy bottom
        listOf(0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0),  // row 11: wavy tips
    )

    val colorMap = mapOf(
        0 to 0x00000000L, // transparent
        1 to 0xFFE8E6DCL, // ghost body (parchment/off-white)
        2 to 0xFFD97757L, // eye whites (accent orange)
        3 to 0xFF141413L, // eye pupils (dark)
        4 to 0xFFFAF9F5L, // body highlight (bright white)
    )

    val palette = SkinPalette(
        accent = 0xFFD97757L,
        accentDeep = 0xFFBD5D3AL,
        background = 0xFF141413L,
        textPrimary = 0xFFFAF9F5L,
        textSecondary = 0xFFB0AEA5L,
        textTertiary = 0xFFE8E6DCL,
    )

    val asciiFrames = mapOf(
        AgentState.IDLE to listOf(
            """
   .-.
  (o o)
  | O |
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.
  (- -)
  | O |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.THINKING to listOf(
            """
   .-.    ?
  (o o)  .
  | ~ | '
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.   ?
  (o o) '
  | ~ |.
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.  ?
  (o o).
  | ~ |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.TOOL_CALL to listOf(
            """
   .-.
  (o o)
  | = |/>
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.
  (o o)
  | = |\>
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.AWAITING_INPUT to listOf(
            """
   .-.
  (O O)  !
  | _ |
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.
  (o o)  !
  | _ |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.ERROR to listOf(
            """
   .-.
  (x x)
  | ~ |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.COMPLETE to listOf(
            """
   .-.
  (^ ^)
  | v |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
    )

    return Skin(
        id = "ghost",
        name = "Ghost",
        description = "Default ghost mascot",
        author = "built-in",
        schemaVersion = 1,
        contentVersion = 1,
        isPremium = false,
        mascot = MascotDef(grid = grid, colorMap = colorMap),
        palette = palette,
        asciiFrames = asciiFrames,
    )
}
