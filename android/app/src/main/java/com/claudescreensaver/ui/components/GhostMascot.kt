package com.claudescreensaver.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.Skin

/**
 * Skin-aware mascot renderer. Draws the mascot pixel grid from the active skin
 * with state-based animations: breathing, wobbling, bouncing, blinking.
 */
@Composable
fun GhostMascot(
    state: AgentState,
    skin: Skin,
    modifier: Modifier = Modifier,
) {
    val mascot = skin.mascot
    val anim = mascot.animation

    val infiniteTransition = rememberInfiniteTransition(label = "ghostAnim")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = anim.breatheScale.first,
        targetValue = anim.breatheScale.second,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    val wobbleX by infiniteTransition.animateFloat(
        initialValue = -anim.wobbleOffset,
        targetValue = anim.wobbleOffset,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobble",
    )

    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -anim.bounceHeight,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )

    val blinkPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(anim.blinkIntervalMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )
    val isBlinking = blinkPhase > 0.9f

    val offsetX = when (state) {
        AgentState.THINKING, AgentState.TOOL_CALL -> wobbleX
        else -> 0f
    }
    val offsetY = when (state) {
        AgentState.AWAITING_INPUT -> bounceY
        else -> 0f
    }
    val scale = when (state) {
        AgentState.IDLE, AgentState.COMPLETE -> breatheScale
        else -> 1f
    }

    Canvas(modifier = modifier) {
        val gridSize = mascot.grid.size
        if (gridSize == 0) return@Canvas
        val cellSize = (minOf(size.width, size.height) / gridSize) * scale
        val totalW = gridSize * cellSize
        val totalH = gridSize * cellSize
        val startX = (size.width - totalW) / 2 + offsetX
        val startY = (size.height - totalH) / 2 + offsetY
        val gap = cellSize * 0.1f

        mascot.grid.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, pixelVal ->
                if (pixelVal == 0) return@forEachIndexed // transparent

                // During blink, eye pupils (3) become eye whites (2)
                val effectiveVal = if (isBlinking && pixelVal == 3) 2 else pixelVal
                val colorLong = mascot.colorMap[effectiveVal] ?: return@forEachIndexed
                val color = Color(colorLong.toInt())

                drawRect(
                    color = color,
                    topLeft = Offset(startX + col * cellSize + gap / 2, startY + row * cellSize + gap / 2),
                    size = Size(cellSize - gap, cellSize - gap),
                )
            }
        }
    }
}
