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

    val statusLabel = when (uiState.agentStatus.state) {
        AgentState.IDLE -> "Idle"
        AgentState.THINKING -> "Thinking"
        AgentState.TOOL_CALL -> uiState.agentStatus.tool ?: "Working"
        AgentState.AWAITING_INPUT -> "Input Required"
        AgentState.ERROR -> "Error"
        AgentState.COMPLETE -> "Complete"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClaudeBgDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) },
        ) {
            ClawdMascot(
                state = uiState.agentStatus.state,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            StatusIndicator(
                state = uiState.agentStatus.state,
                modifier = Modifier.padding(bottom = 16.dp),
            )

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

            if (uiState.agentStatus.requiresInput && uiState.agentStatus.message.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = uiState.agentStatus.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(48.dp))
            ConnectionBadge(
                state = uiState.connectionState,
                instanceName = uiState.agentStatus.instanceName,
            )
        }
    }
}
