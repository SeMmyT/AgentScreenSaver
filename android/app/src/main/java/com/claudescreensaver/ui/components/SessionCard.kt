package com.claudescreensaver.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.AgentStatus
import com.claudescreensaver.ui.theme.*

@Composable
fun SessionCard(
    status: AgentStatus,
    modifier: Modifier = Modifier,
) {
    val stateColor = when (status.state) {
        AgentState.IDLE -> StatusDisabled
        AgentState.THINKING -> StatusStandby
        AgentState.TOOL_CALL -> StatusRunning
        AgentState.AWAITING_INPUT -> StatusWarning
        AgentState.ERROR -> StatusCritical
        AgentState.COMPLETE -> ClaudeAccent
    }

    val animatedColor by animateColorAsState(
        targetValue = stateColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardColor",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "cardPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status.state == AgentState.AWAITING_INPUT) 600 else 2000,
                easing = EaseInOut,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val stateLabel = when (status.state) {
        AgentState.IDLE -> "Idle"
        AgentState.THINKING -> "Thinking"
        AgentState.TOOL_CALL -> status.tool ?: "Working"
        AgentState.AWAITING_INPUT -> "Input Required"
        AgentState.ERROR -> "Error"
        AgentState.COMPLETE -> "Complete"
    }

    // Truncate session ID for display
    val shortId = status.sessionId.take(8)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ClaudeBgDark)
            .border(1.dp, animatedColor.copy(alpha = pulseAlpha), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top: status dot + state label
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(animatedColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Middle: tool/task summary or message
            val displayText = when {
                status.toolInputSummary.isNotEmpty() -> status.toolInputSummary
                status.message.isNotEmpty() -> status.message
                else -> ""
            }
            if (displayText.isNotEmpty()) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                    color = ClaudeGray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            Spacer(Modifier.height(4.dp))

            // Bottom: session ID
            Text(
                text = shortId,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = ClaudeGray.copy(alpha = 0.5f),
            )
        }
    }
}
