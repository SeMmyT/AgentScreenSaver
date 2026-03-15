package com.claudescreensaver.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.claudescreensaver.data.network.BridgeServer
import com.claudescreensaver.data.network.ConnectionState
import com.claudescreensaver.ui.components.ConnectionBadge
import com.claudescreensaver.ui.theme.*
import com.claudescreensaver.viewmodel.UiState

@Composable
fun SettingsScreen(
    uiState: UiState,
    discoveredServers: List<BridgeServer>,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("claude_screensaver", Context.MODE_PRIVATE)
    var serverUrl by remember {
        mutableStateOf(prefs.getString("server_url", "") ?: "")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Agent ScreenSaver",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(24.dp))

        // Connection status
        ConnectionBadge(
            state = uiState.connectionState,
            instanceName = uiState.agentStatus.instanceName,
        )

        Spacer(Modifier.height(24.dp))

        // Server URL input
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Bridge Server URL") },
            placeholder = { Text("http://192.168.1.100:4001/events") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ClaudeAccent,
                unfocusedBorderColor = ClaudeGray,
                focusedLabelColor = ClaudeAccent,
                cursorColor = ClaudeAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // Connect / Disconnect buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.connectionState == ConnectionState.CONNECTED ||
                uiState.connectionState == ConnectionState.CONNECTING) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClaudeAccentDeep,
                    ),
                ) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = {
                        if (serverUrl.isNotBlank()) {
                            prefs.edit().putString("server_url", serverUrl).apply()
                            onConnect(serverUrl)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ClaudeAccent,
                    ),
                    enabled = serverUrl.isNotBlank(),
                ) {
                    Text("Connect")
                }
            }
        }

        // Discovered servers section
        if (discoveredServers.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Discovered Servers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            discoveredServers.forEach { server ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            serverUrl = server.sseUrl
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = server.sseUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = ClaudeGray,
                        )
                    }
                }
            }
        }

        // Display mode toggle
        Spacer(Modifier.height(24.dp))
        var displayMode by remember {
            mutableStateOf(prefs.getString("display_mode", "advanced") ?: "advanced")
        }
        Text(
            text = "Display Mode",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("advanced" to "Advanced", "simple" to "Simple").forEach { (value, label) ->
                FilterChip(
                    selected = displayMode == value,
                    onClick = {
                        displayMode = value
                        prefs.edit().putString("display_mode", value).apply()
                    },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ClaudeAccent,
                        selectedLabelColor = ClaudeBgDark,
                    ),
                )
            }
        }
        Text(
            text = if (displayMode == "advanced") "4-pane terminal grid"
                   else "Full-screen ASCII animations",
            style = MaterialTheme.typography.labelSmall,
            color = ClaudeGray,
        )

        // Kiosk mode toggle
        Spacer(Modifier.height(24.dp))
        var kioskMode by remember {
            mutableStateOf(prefs.getBoolean("kiosk_mode", false))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Kiosk Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Show over lock screen while charging",
                    style = MaterialTheme.typography.labelSmall,
                    color = ClaudeGray,
                )
            }
            Switch(
                checked = kioskMode,
                onCheckedChange = { checked ->
                    kioskMode = checked
                    prefs.edit().putBoolean("kiosk_mode", checked).apply()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ClaudeAccent,
                    checkedTrackColor = ClaudeAccentDeep,
                ),
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = if (kioskMode) "Kiosk: app shows over lock screen when charging. Place on stand and it takes over."
                   else "Tip: Enable Kiosk Mode above, or go to Settings > Display > Screen Saver.",
            style = MaterialTheme.typography.labelSmall,
            color = ClaudeGray,
        )
    }
}
