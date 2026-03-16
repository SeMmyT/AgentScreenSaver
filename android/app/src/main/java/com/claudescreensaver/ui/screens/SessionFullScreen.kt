package com.claudescreensaver.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.AgentStatus
import com.claudescreensaver.ui.theme.*

/**
 * Full-screen view of a single session with input capability.
 * Maximizes text display. Shows input field when user taps or presses back.
 */
@Composable
fun SessionFullScreen(
    status: AgentStatus,
    onBack: () -> Unit,
    onSendInput: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInput by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val mono = FontFamily.Monospace
    val termBg = Color(0xFF0D0D0D)
    val termBorder = Color(0xFF2A2A2A)

    val canInput = status.state == AgentState.COMPLETE ||
            status.state == AgentState.AWAITING_INPUT ||
            status.state == AgentState.IDLE

    // Back handler: if input is showing, hide it; otherwise go back to grid
    BackHandler {
        if (showInput) {
            showInput = false
        } else {
            // Toggle input on back press (acts like ESC)
            if (canInput) {
                showInput = true
            } else {
                onBack()
            }
        }
    }

    // Auto-focus when input appears
    LaunchedEffect(showInput) {
        if (showInput) {
            focusRequester.requestFocus()
        }
    }

    val stateColor = when (status.state) {
        AgentState.IDLE -> StatusDisabled
        AgentState.THINKING -> StatusStandby
        AgentState.TOOL_CALL -> StatusRunning
        AgentState.AWAITING_INPUT -> StatusWarning
        AgentState.ERROR -> StatusCritical
        AgentState.COMPLETE -> ClaudeAccent
    }

    val infiniteTransition = rememberInfiniteTransition(label = "fsPulse")
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
        label = "fsPulseAlpha",
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClaudeBgDark)
            .padding(8.dp),
    ) {
        // Terminal frame
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, termBorder, RoundedCornerShape(6.dp))
                .background(termBg),
        ) {
            // Title bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(stateColor))
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(stateColor.copy(alpha = 0.4f)))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "$shortId — $stateLabel",
                    fontFamily = mono,
                    fontSize = 12.sp,
                    color = ClaudeGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (status.subAgents.isNotEmpty()) {
                    Text(
                        text = "${status.subAgents.size} agents",
                        fontFamily = mono,
                        fontSize = 10.sp,
                        color = StatusStandby,
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Back hint
                Text(
                    text = "< BACK",
                    fontFamily = mono,
                    fontSize = 10.sp,
                    color = ClaudeGray.copy(alpha = 0.5f),
                )
            }

            // Scrollable content — maximize text display
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(10.dp),
            ) {
                // Tool line
                if (status.tool != null) {
                    Row {
                        Text("❯ ", fontFamily = mono, fontSize = 13.sp, color = ClaudeAccent)
                        Text(status.tool ?: "", fontFamily = mono, fontSize = 13.sp, color = StatusRunning)
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Tool input summary
                if (status.toolInputSummary.isNotEmpty()) {
                    Text(
                        text = status.toolInputSummary,
                        fontFamily = mono,
                        fontSize = 12.sp,
                        color = ClaudeGray.copy(alpha = 0.8f),
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // User input
                status.userMessage?.let { msg ->
                    Text(
                        text = "you: $msg",
                        fontFamily = mono,
                        fontSize = 13.sp,
                        color = ClaudeAccent,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // Interrupted
                if (status.interrupted) {
                    Text(
                        text = ">>> INTERRUPTED <<<",
                        fontFamily = mono,
                        fontSize = 15.sp,
                        color = StatusWarning,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // Message — full text, no maxLines limit
                if (status.message.isNotEmpty()) {
                    Text(
                        text = status.message,
                        fontFamily = mono,
                        fontSize = 12.sp,
                        color = if (status.requiresInput) StatusWarning else ClaudeParchment,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // Sub-agents — full list
                if (status.subAgents.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "sub-agents:",
                        fontFamily = mono,
                        fontSize = 11.sp,
                        color = ClaudeGray,
                    )
                    status.subAgents.forEach { agent ->
                        Text(
                            text = "  ${if (agent.status == "running") ">" else "-"} ${agent.name.ifEmpty { agent.agentType }}",
                            fontFamily = mono,
                            fontSize = 11.sp,
                            color = if (agent.status == "running") StatusRunning else StatusDisabled,
                        )
                    }
                }

                // Bottom status
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = status.event.lowercase(),
                        fontFamily = mono,
                        fontSize = 10.sp,
                        color = ClaudeGray.copy(alpha = 0.4f),
                    )
                    if (canInput) {
                        Text(
                            text = "tap to type",
                            fontFamily = mono,
                            fontSize = 10.sp,
                            color = ClaudeAccent.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        // Input bar — always visible when canInput, expandable
        if (canInput) {
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1A1A1A))
                    .border(1.dp, if (showInput) ClaudeAccent else termBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("❯ ", fontFamily = mono, fontSize = 14.sp, color = ClaudeAccent)
                if (showInput) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "type your message...",
                                fontFamily = mono,
                                fontSize = 13.sp,
                                color = ClaudeGray.copy(alpha = 0.4f),
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = mono,
                            fontSize = 13.sp,
                            color = ClaudeTextLight,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    onSendInput(inputText.trim())
                                    inputText = ""
                                    showInput = false
                                }
                            }
                        ),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = ClaudeAccent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )
                } else {
                    Text(
                        text = "tap to type...",
                        fontFamily = mono,
                        fontSize = 13.sp,
                        color = ClaudeGray.copy(alpha = 0.4f),
                        modifier = Modifier
                            .weight(1f)
                            .noRippleClickable { showInput = true },
                    )
                }
            }
        }
    }
}

/**
 * Clickable without ripple effect — for subtle tap targets.
 */
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { onClick() })
        }
    )
}
