package com.claudescreensaver.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.AgentStatus
import com.claudescreensaver.ui.theme.*

/**
 * Terminal-style session pane mimicking Claude Code's split-view layout.
 * Dark background, monospace text, title bar with status dot + session info.
 */
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
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status.state == AgentState.AWAITING_INPUT) 600 else 2000,
                easing = EaseInOut,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val stateLabel = status.customLabel ?: when (status.state) {
        AgentState.IDLE -> "idle"
        AgentState.THINKING -> "thinking..."
        AgentState.TOOL_CALL -> status.tool?.lowercase() ?: "working"
        AgentState.AWAITING_INPUT -> "waiting for input"
        AgentState.ERROR -> "error"
        AgentState.COMPLETE -> "done"
    }

    val shortId = status.sessionId.take(8)
    val mono = FontFamily.Monospace
    val termBg = Color(0xFF0D0D0D)
    val termBorder = Color(0xFF2A2A2A)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, termBorder, RoundedCornerShape(6.dp))
            .background(termBg),
    ) {
        // Title bar — like a terminal tab
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            // Traffic light dots
            Box(Modifier.size(8.dp).clip(CircleShape).background(animatedColor))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(animatedColor.copy(alpha = 0.4f)))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$shortId — $stateLabel",
                fontFamily = mono,
                fontSize = 10.sp,
                color = ClaudeGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (status.subAgents.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${status.subAgents.size} agents",
                    fontFamily = mono,
                    fontSize = 9.sp,
                    color = StatusStandby,
                )
            }
        }

        // Terminal content area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            // Tool/event line
            if (status.tool != null) {
                Row {
                    Text(
                        text = "❯ ",
                        fontFamily = mono,
                        fontSize = 11.sp,
                        color = ClaudeAccent,
                    )
                    Text(
                        text = status.tool ?: "",
                        fontFamily = mono,
                        fontSize = 11.sp,
                        color = StatusRunning,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }

            // Tool input summary (like command output)
            if (status.toolInputSummary.isNotEmpty()) {
                Text(
                    text = status.toolInputSummary,
                    fontFamily = mono,
                    fontSize = 10.sp,
                    color = ClaudeGray.copy(alpha = 0.8f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
            }

            // User input (from UserPromptSubmit)
            status.userMessage?.let { msg ->
                Text(
                    text = "you: $msg",
                    fontFamily = mono,
                    fontSize = 11.sp,
                    color = ClaudeAccent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }

            // Interrupted state
            if (status.interrupted) {
                Text(
                    text = ">>> INTERRUPTED <<<",
                    fontFamily = mono,
                    fontSize = 13.sp,
                    color = StatusWarning,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(pulseAlpha),
                )
                Spacer(Modifier.height(4.dp))
            }

            // Message (thinking content, notification, etc.)
            if (status.message.isNotEmpty()) {
                Text(
                    text = status.message,
                    fontFamily = mono,
                    fontSize = 10.sp,
                    color = if (status.requiresInput) StatusWarning else ClaudeParchment,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                )
            }

            // Sub-agent list
            if (status.subAgents.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                status.subAgents.forEach { agent ->
                    Text(
                        text = "  ${if (agent.status == "running") ">" else "-"} ${agent.name.ifEmpty { agent.agentType }}",
                        fontFamily = mono,
                        fontSize = 9.sp,
                        color = if (agent.status == "running") StatusRunning else StatusDisabled,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom status line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = status.event.lowercase(),
                    fontFamily = mono,
                    fontSize = 9.sp,
                    color = ClaudeGray.copy(alpha = 0.4f),
                )
                if (status.agentType != null) {
                    Text(
                        text = status.agentType ?: "",
                        fontFamily = mono,
                        fontSize = 9.sp,
                        color = ClaudeAccent.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
