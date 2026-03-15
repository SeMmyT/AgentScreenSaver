package com.claudescreensaver.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.AgentStatus
import com.claudescreensaver.ui.components.ClawdMascot
import com.claudescreensaver.ui.components.ConnectionBadge
import com.claudescreensaver.ui.components.SessionCard
import com.claudescreensaver.ui.components.StatusIndicator
import com.claudescreensaver.ui.theme.ClaudeBgDark
import com.claudescreensaver.ui.theme.ClaudeGray
import com.claudescreensaver.ui.theme.ClaudeAccent
import com.claudescreensaver.viewmodel.UiState
import kotlin.math.roundToInt

@Composable
fun StatusDashboardScreen(
    uiState: UiState,
    isPro: Boolean = true,
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

    val maxPanes = if (isPro) 4 else 1
    val activeSessions = uiState.sessions.values
        .sortedByDescending { it.timestamp }
        .take(maxPanes)

    // Reorder state: maps slot index to session ID
    var slotOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    // Track which slot is selected for swap (long-press mode)
    var selectedSlot by remember { mutableIntStateOf(-1) }

    // Sync slot order with incoming sessions, preserving user order
    val sessionIds = activeSessions.map { it.sessionId }
    val currentOrder = remember(sessionIds.toSet()) {
        // Keep existing ordered IDs that are still active, append new ones
        val kept = slotOrder.filter { it in sessionIds }
        val newIds = sessionIds.filter { it !in kept }
        kept + newIds
    }
    LaunchedEffect(currentOrder) { slotOrder = currentOrder }

    // Build ordered list
    val orderedSessions = slotOrder.mapNotNull { id ->
        activeSessions.find { it.sessionId == id }
    }

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
                .padding(8.dp),
        ) {
            // Top bar: Clawd + connection badge (hidden in free tier)
            if (isPro) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClawdMascot(
                            state = uiState.agentStatus.state,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Agent Code",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    ConnectionBadge(
                        state = uiState.connectionState,
                        instanceName = uiState.agentStatus.instanceName,
                    )
                }
            }

            when {
                orderedSessions.size >= 2 -> {
                    // 2x2 grid (both portrait and landscape)
                    val rows = orderedSessions.chunked(2)
                    rows.forEachIndexed { rowIdx, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEachIndexed { colIdx, session ->
                                val slotIdx = rowIdx * 2 + colIdx
                                val isSelected = selectedSlot == slotIdx

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(vertical = 2.dp)
                                        .then(
                                            if (isSelected) Modifier.background(
                                                ClaudeAccent.copy(alpha = 0.15f)
                                            ) else Modifier
                                        )
                                        .pointerInput(slotIdx) {
                                            detectTapGestures(
                                                onLongPress = {
                                                    selectedSlot = if (isSelected) -1 else slotIdx
                                                },
                                                onTap = {
                                                    if (selectedSlot >= 0 && selectedSlot != slotIdx) {
                                                        // Swap slots
                                                        val newOrder = slotOrder.toMutableList()
                                                        val fromIdx = selectedSlot
                                                        val toIdx = slotIdx
                                                        if (fromIdx < newOrder.size && toIdx < newOrder.size) {
                                                            val tmp = newOrder[fromIdx]
                                                            newOrder[fromIdx] = newOrder[toIdx]
                                                            newOrder[toIdx] = tmp
                                                            slotOrder = newOrder
                                                        }
                                                        selectedSlot = -1
                                                    }
                                                },
                                            )
                                        },
                                ) {
                                    SessionCard(
                                        status = session,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    // Selection indicator
                                    if (isSelected) {
                                        Text(
                                            text = "TAP TARGET TO SWAP",
                                            fontSize = 9.sp,
                                            color = ClaudeAccent,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 2.dp),
                                        )
                                    }
                                }
                            }
                            if (row.size < 2) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                orderedSessions.size == 1 -> {
                    // Single session: full terminal pane
                    SessionCard(
                        status = orderedSessions.first(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 2.dp),
                    )
                }
                else -> {
                    // No sessions: centered status display
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
}
