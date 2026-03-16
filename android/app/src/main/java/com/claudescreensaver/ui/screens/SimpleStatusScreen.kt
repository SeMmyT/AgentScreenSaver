package com.claudescreensaver.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.AgentStatus
import com.claudescreensaver.data.models.Skin
import com.claudescreensaver.ui.theme.*
import com.claudescreensaver.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SimpleStatusScreen(
    uiState: UiState,
    isPro: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Burn-in prevention: Lissajous pixel shift
    val infiniteTransition = rememberInfiniteTransition(label = "simpleShift")
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

    // Reorder state (same pattern as Advanced mode)
    var slotOrder by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSlot by remember { mutableIntStateOf(-1) }

    val sessionIds = activeSessions.map { it.sessionId }
    val currentOrder = remember(sessionIds.toSet()) {
        val kept = slotOrder.filter { it in sessionIds }
        val newIds = sessionIds.filter { it !in kept }
        kept + newIds
    }
    LaunchedEffect(currentOrder) { slotOrder = currentOrder }

    val orderedSessions = slotOrder.mapNotNull { id ->
        activeSessions.find { it.sessionId == id }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClaudeBgDark),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(8.dp),
        ) {
            when {
                orderedSessions.size >= 2 -> {
                    // 2x2 grid of ASCII panes with reorder support
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
                                        .padding(2.dp)
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
                                    SimpleAsciiPane(
                                        status = session,
                                        compact = true,
                                        skin = uiState.activeSkin,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "TAP TARGET TO SWAP",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
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
                    SimpleAsciiPane(
                        status = orderedSessions.first(),
                        compact = false,
                        skin = uiState.activeSkin,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    SimpleAsciiPane(
                        status = uiState.agentStatus,
                        compact = false,
                        skin = uiState.activeSkin,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * A single ASCII animation pane. When [compact] is true, uses smaller
 * font sizes for 2x2 grid layout.
 */
@Composable
private fun SimpleAsciiPane(
    status: AgentStatus,
    compact: Boolean,
    skin: Skin = Skin.DEFAULT,
    modifier: Modifier = Modifier,
) {
    val frames = status.customFrames?.takeIf { it.isNotEmpty() }
        ?: skin.asciiFrames[status.state]
        ?: listOf("?")
    var frameIndex by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(status.state) {
        frameIndex = 0
        tick = 0
        while (true) {
            delay(600)
            frameIndex = (frameIndex + 1) % frames.size
            // Rotate fun words every 4 frames (~2.4s)
            if (frameIndex == 0) tick++
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "paneP-${status.sessionId}")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status.state == AgentState.AWAITING_INPUT) 500 else 2000,
                easing = EaseInOut,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val mono = FontFamily.Monospace
    val artSize = if (compact) 10.sp else 14.sp
    val artLineHeight = if (compact) 12.sp else 16.sp
    val labelSize = if (compact) 14.sp else 24.sp
    val contextSize = if (compact) 9.sp else 13.sp
    val detailSize = if (compact) 8.sp else 11.sp

    Box(
        modifier = modifier.background(ClaudeBgDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(if (compact) 4.dp else 24.dp),
        ) {
            // Pane title: best description of what this session is doing
            if (compact && status.sessionId.isNotEmpty()) {
                val title = status.tool?.let { "$it: ${status.toolInputSummary.take(30)}" }
                    ?: status.agentType
                    ?: status.sessionId.take(8)
                Text(
                    text = title,
                    color = stateColor(status.state).copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // ASCII art
            Text(
                text = frames[frameIndex],
                color = stateColor(status.state).copy(alpha = pulseAlpha),
                fontSize = artSize,
                fontFamily = mono,
                lineHeight = artLineHeight,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 32.dp))

            // State label
            Text(
                text = status.customLabel ?: stateLabel(status.state, tick),
                color = ClaudeTextLight,
                fontSize = labelSize,
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
            )

            // Context line
            val context = status.userMessage
                ?: status.tool
                ?: status.message.takeIf { it.isNotBlank() }
            if (context != null) {
                Text(
                    text = context,
                    color = ClaudeGray,
                    fontSize = contextSize,
                    fontFamily = mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (compact) 2.dp else 8.dp),
                )
            }

            // Tool input summary
            if (status.toolInputSummary.isNotBlank() && status.tool != null) {
                Text(
                    text = status.toolInputSummary,
                    color = ClaudeGray.copy(alpha = 0.6f),
                    fontSize = detailSize,
                    fontFamily = mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (compact) 1.dp else 4.dp),
                )
            }

            // Compact metrics line
            val metrics = buildString {
                status.model?.let { append(it.take(12)) }
                status.contextPercent?.let {
                    if (isNotEmpty()) append(" ")
                    append("ctx:${it.toInt()}%")
                }
                status.costUsd?.let {
                    if (isNotEmpty()) append(" ")
                    append("$${String.format("%.2f", it)}")
                }
                if (status.linesAdded != null) {
                    if (isNotEmpty()) append(" ")
                    append("+${status.linesAdded}/-${status.linesRemoved ?: 0}")
                }
            }
            if (metrics.isNotEmpty()) {
                Text(
                    text = metrics,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (compact) 8.sp else 10.sp,
                    color = ClaudeGray.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
            }

            if (!compact) {
                // Sub-agent count (full mode only — no room in compact)
                val agentCount = status.subAgents.size
                if (agentCount > 0) {
                    Text(
                        text = "$agentCount agent${if (agentCount > 1) "s" else ""} running",
                        color = StatusStandby,
                        fontSize = 12.sp,
                        fontFamily = mono,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // Interrupted indicator
                if (status.interrupted) {
                    Text(
                        text = ">>> INTERRUPTED <<<",
                        color = StatusWarning,
                        fontSize = 14.sp,
                        fontFamily = mono,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                // Compact: single-line sub-agent + interrupt hints
                if (status.subAgents.isNotEmpty()) {
                    Text(
                        text = "${status.subAgents.size} agents",
                        color = StatusStandby,
                        fontSize = detailSize,
                        fontFamily = mono,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (status.interrupted) {
                    Text(
                        text = "INTERRUPTED",
                        color = StatusWarning,
                        fontSize = detailSize,
                        fontFamily = mono,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}


@Composable
private fun stateColor(state: AgentState): Color = when (state) {
    AgentState.IDLE -> StatusDisabled
    AgentState.THINKING -> StatusStandby
    AgentState.TOOL_CALL -> StatusRunning
    AgentState.AWAITING_INPUT -> StatusWarning
    AgentState.ERROR -> StatusCritical
    AgentState.COMPLETE -> ClaudeAccent
}

// Extracted from Claude Code binary — the actual spinner words CC uses
private val thinkingWords = listOf(
    "Thinking...", "Pondering...", "Ruminating...", "Discombobulating...",
    "Cogitating...", "Musing...", "Deliberating...", "Contemplating...",
    "Noodling...", "Percolating...", "Mulling...", "Ideating...",
    "Philosophising...", "Cerebrating...", "Elucidating...", "Fermenting...",
    "Incubating...", "Germinating...", "Crystallizing...", "Synthesizing...",
    "Harmonizing...", "Orchestrating...", "Concocting...", "Brewing...",
    "Simmering...", "Stewing...", "Marinating...", "Churning...",
    "Perambulating...", "Meandering...", "Gallivanting...", "Lollygagging...",
    "Puttering...", "Tinkering...", "Dithering...", "Frolicking...",
    "Canoodling...", "Boondoggling...", "Shenaniganing...", "Tomfoolering...",
    "Flibbertigibbeting...", "Razzmatazzing...", "Whatchamacalliting...",
    "Combobulating...", "Recombobulating...", "Prestidigitating...",
    "Metamorphosing...", "Transmuting...", "Transfiguring...", "Sublimating...",
    "Nebulizing...", "Reticulating...", "Nucleating...", "Osmosing...",
    "Photosynthesizing...", "Quantumizing...", "Hyperspacing...",
    "Pontificating...", "Bloviating...", "Gesticulating...", "Undulating...",
    "Spelunking...", "Manifesting...", "Levitating...", "Moonwalking...",
    "Waddling...", "Scampering...", "Moseying...", "Skedaddling...",
    "Schlepping...", "Swooping...", "Fluttering...", "Shimmying...",
    "Jitterbugging...", "Boogieing...", "Grooving...", "Vibing...",
    "Clauding...", "Hullaballooing...", "Whirlpooling...", "Zigzagging...",
)

private val workingWords = listOf(
    "Working...", "Crafting...", "Forging...", "Computing...",
    "Generating...", "Composing...", "Creating...", "Hatching...",
    "Cooking...", "Baking...", "Kneading...", "Seasoning...",
    "Garnishing...", "Julienning...", "Frosting...", "Zesting...",
    "Crunching...", "Wrangling...", "Finagling...", "Improvising...",
    "Architecting...", "Accomplishing...", "Actualizing...",
)

private fun stateLabel(state: AgentState, tick: Int = 0): String = when (state) {
    AgentState.IDLE -> "idle"
    AgentState.THINKING -> thinkingWords[tick % thinkingWords.size]
    AgentState.TOOL_CALL -> workingWords[tick % workingWords.size]
    AgentState.AWAITING_INPUT -> "your turn"
    AgentState.ERROR -> "error"
    AgentState.COMPLETE -> "done"
}

private fun stateWeight(state: AgentState): Int = when (state) {
    AgentState.AWAITING_INPUT -> 5
    AgentState.ERROR -> 4
    AgentState.TOOL_CALL -> 3
    AgentState.THINKING -> 2
    AgentState.IDLE -> 1
    AgentState.COMPLETE -> 0
}
