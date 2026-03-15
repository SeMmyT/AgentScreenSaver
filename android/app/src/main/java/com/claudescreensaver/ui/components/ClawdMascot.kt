package com.claudescreensaver.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.ui.theme.ClaudeAccent
import com.claudescreensaver.ui.theme.ClaudeBgDark

/**
 * Clawd — the 8-bit pixel-art crab mascot from Claude Code.
 * 12x12 grid with eyes, claws, body, and legs.
 * Animates based on agent state: breathe, wobble, bounce, blink.
 */
@Composable
fun ClawdMascot(
    state: AgentState,
    modifier: Modifier = Modifier,
    tint: Color = ClaudeAccent,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "clawd")

    // Breathing scale (idle/complete)
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    // Wobble for thinking/tool_call
    val wobbleX by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobble",
    )

    // Bounce for awaiting input
    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )

    // Blink cycle (eyes close briefly)
    val blinkPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )
    val isBlinking = blinkPhase > 0.92f && blinkPhase < 0.96f

    // Claw wave for awaiting input
    val clawWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "clawWave",
    )

    val scale = when (state) {
        AgentState.IDLE, AgentState.COMPLETE -> breatheScale
        else -> 1f
    }

    val extraOffsetX = when (state) {
        AgentState.THINKING, AgentState.TOOL_CALL -> wobbleX
        else -> 0f
    }

    val extraOffsetY = when (state) {
        AgentState.AWAITING_INPUT -> bounceY
        else -> 0f
    }

    // Eye color: dark background for pupils
    val eyeColor = ClaudeBgDark

    Canvas(modifier = modifier.size(80.dp)) {
        val gridSize = 12
        val pixelSize = size.width / gridSize
        val gap = pixelSize * 0.08f
        val blockSize = pixelSize - gap

        fun drawPixel(col: Int, row: Int, color: Color) {
            val x = (col * pixelSize * scale) + (size.width / 2f * (1 - scale)) + extraOffsetX
            val y = (row * pixelSize * scale) + (size.height / 2f * (1 - scale)) + extraOffsetY
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(blockSize * scale, blockSize * scale),
            )
        }

        // Clawd pixel art (12x12 grid)
        // Row 0: eye stalks
        drawPixel(3, 0, tint)
        drawPixel(8, 0, tint)

        // Row 1: eye bulbs
        drawPixel(2, 1, tint)
        drawPixel(3, 1, tint)
        drawPixel(4, 1, tint)
        drawPixel(7, 1, tint)
        drawPixel(8, 1, tint)
        drawPixel(9, 1, tint)

        // Row 2: eyes with pupils (blink = filled)
        drawPixel(2, 2, tint)
        drawPixel(3, 2, if (isBlinking) tint else eyeColor) // left pupil
        drawPixel(4, 2, tint)
        drawPixel(7, 2, tint)
        drawPixel(8, 2, if (isBlinking) tint else eyeColor) // right pupil
        drawPixel(9, 2, tint)

        // Row 3: claw tops (raised when awaiting input)
        val clawRaise = if (state == AgentState.AWAITING_INPUT) (clawWave * -1).toInt() else 0
        drawPixel(0, 3 + clawRaise, tint)
        drawPixel(1, 3, tint)
        drawPixel(10, 3, tint)
        drawPixel(11, 3 + clawRaise, tint)

        // Row 4: claws + upper body
        drawPixel(0, 4, tint)
        drawPixel(1, 4, tint)
        drawPixel(2, 4, tint)
        drawPixel(3, 4, tint)
        drawPixel(4, 4, tint)
        drawPixel(5, 4, tint)
        drawPixel(6, 4, tint)
        drawPixel(7, 4, tint)
        drawPixel(8, 4, tint)
        drawPixel(9, 4, tint)
        drawPixel(10, 4, tint)
        drawPixel(11, 4, tint)

        // Row 5: body
        drawPixel(2, 5, tint)
        drawPixel(3, 5, tint)
        drawPixel(4, 5, tint)
        drawPixel(5, 5, tint)
        drawPixel(6, 5, tint)
        drawPixel(7, 5, tint)
        drawPixel(8, 5, tint)
        drawPixel(9, 5, tint)

        // Row 6: body with shell pattern
        drawPixel(1, 6, tint)
        drawPixel(2, 6, tint)
        drawPixel(3, 6, tint)
        drawPixel(4, 6, tint)
        drawPixel(5, 6, tint)
        drawPixel(6, 6, tint)
        drawPixel(7, 6, tint)
        drawPixel(8, 6, tint)
        drawPixel(9, 6, tint)
        drawPixel(10, 6, tint)

        // Row 7: body core
        drawPixel(2, 7, tint)
        drawPixel(3, 7, tint)
        drawPixel(4, 7, tint)
        drawPixel(5, 7, tint)
        drawPixel(6, 7, tint)
        drawPixel(7, 7, tint)
        drawPixel(8, 7, tint)
        drawPixel(9, 7, tint)

        // Row 8: lower body
        drawPixel(3, 8, tint)
        drawPixel(4, 8, tint)
        drawPixel(5, 8, tint)
        drawPixel(6, 8, tint)
        drawPixel(7, 8, tint)
        drawPixel(8, 8, tint)

        // Row 9: legs
        drawPixel(2, 9, tint)
        drawPixel(3, 9, tint)
        drawPixel(5, 9, tint)
        drawPixel(6, 9, tint)
        drawPixel(8, 9, tint)
        drawPixel(9, 9, tint)

        // Row 10: leg tips
        drawPixel(1, 10, tint)
        drawPixel(4, 10, tint)
        drawPixel(7, 10, tint)
        drawPixel(10, 10, tint)
    }
}
