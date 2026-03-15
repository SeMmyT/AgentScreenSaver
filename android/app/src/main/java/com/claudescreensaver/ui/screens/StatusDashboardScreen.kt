package com.claudescreensaver.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.ui.components.ClawdMascot
import com.claudescreensaver.ui.components.ConnectionBadge
import com.claudescreensaver.ui.components.SessionCard
import com.claudescreensaver.ui.components.StatusIndicator
import com.claudescreensaver.ui.theme.ClaudeBgDark
import com.claudescreensaver.ui.theme.ClaudeGray
import com.claudescreensaver.viewmodel.UiState
import kotlin.math.roundToInt

@Composable
fun StatusDashboardScreen(
    uiState: UiState,
    modifier: Modifier = Modifier,
) {
    // Burn-in prevention: Lissajous pixel shift
    val infiniteTransition = rememberInfiniteTransition(label = "pixelShift")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shiftX",
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 90_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shiftY",
    )

    val activeSessions = uiState.sessions.values
        .sortedByDescending { it.timestamp }
        .take(4)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClaudeBgDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(16.dp),
        ) {
            // Top bar: Clawd + connection badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClawdMascot(
                        state = uiState.agentStatus.state,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Claude Code",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                ConnectionBadge(
                    state = uiState.connectionState,
                    instanceName = uiState.agentStatus.instanceName,
                )
            }

            if (activeSessions.size > 1) {
                // 2x2 grid of session cards
                val rows = activeSessions.chunked(2)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { session ->
                            SessionCard(
                                status = session,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(vertical = 4.dp),
                            )
                        }
                        // Fill empty slots in last row
                        if (row.size < 2) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                // Single session: centered layout with big status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    StatusIndicator(
                        state = uiState.agentStatus.state,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    val statusLabel = when (uiState.agentStatus.state) {
                        AgentState.IDLE -> "Idle"
                        AgentState.THINKING -> "Thinking"
                        AgentState.TOOL_CALL -> uiState.agentStatus.tool ?: "Working"
                        AgentState.AWAITING_INPUT -> "Input Required"
                        AgentState.ERROR -> "Error"
                        AgentState.COMPLETE -> "Complete"
                    }

                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    if (uiState.agentStatus.toolInputSummary.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = uiState.agentStatus.toolInputSummary,
                            style = MaterialTheme.typography.headlineMedium,
                            color = ClaudeGray,
                            maxLines = 2,
                        )
                    }

                    if (uiState.agentStatus.message.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = uiState.agentStatus.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (uiState.agentStatus.requiresInput)
                                MaterialTheme.colorScheme.primary
                            else ClaudeGray,
                            maxLines = 3,
                        )
                    }
                }
            }
        }
    }
}
