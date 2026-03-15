package com.claudescreensaver

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudescreensaver.data.SoundManager
import com.claudescreensaver.data.network.BridgeDiscovery
import com.claudescreensaver.data.network.ConnectionState
import com.claudescreensaver.data.network.SseClient
import com.claudescreensaver.ui.screens.SettingsScreen
import com.claudescreensaver.ui.screens.StatusDashboardScreen
import com.claudescreensaver.ui.theme.ClaudeAccent
import com.claudescreensaver.ui.theme.ClaudeScreenSaverTheme
import com.claudescreensaver.viewmodel.StatusViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bridgeDiscovery: BridgeDiscovery
    private lateinit var viewModel: StatusViewModel
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bridgeDiscovery = BridgeDiscovery(this)
        viewModel = StatusViewModel(SseClient())
        soundManager = SoundManager(this)

        // Autoconnect: if we have a saved URL, connect immediately
        val prefs = getSharedPreferences("claude_screensaver", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "") ?: ""
        if (savedUrl.isNotBlank()) {
            viewModel.connect(savedUrl)
        }

        setContent {
            ClaudeScreenSaverTheme {
                var showPreview by remember { mutableStateOf(false) }
                val uiState by viewModel.uiState.collectAsState()
                val servers by bridgeDiscovery.servers.collectAsState()
                val scope = rememberCoroutineScope()

                // Play sounds on state changes
                LaunchedEffect(uiState.agentStatus.state) {
                    soundManager.onStateChange(uiState.agentStatus.state)
                }

                // Autoconnect from mDNS: if disconnected and no saved URL,
                // connect to the first discovered server automatically
                LaunchedEffect(servers) {
                    if (servers.isNotEmpty() &&
                        uiState.connectionState == ConnectionState.DISCONNECTED &&
                        savedUrl.isBlank()
                    ) {
                        val server = servers.first()
                        prefs.edit().putString("server_url", server.sseUrl).apply()
                        viewModel.connect(server.sseUrl)
                    }
                }

                if (showPreview) {
                    StatusDashboardScreen(
                        uiState = uiState,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column {
                        SettingsScreen(
                            uiState = uiState,
                            discoveredServers = servers,
                            onConnect = { url -> viewModel.connect(url) },
                            onDisconnect = { viewModel.disconnect() },
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { showPreview = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ClaudeAccent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        ) {
                            Text("Preview ScreenSaver")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bridgeDiscovery.startDiscovery()
    }

    override fun onPause() {
        bridgeDiscovery.stopDiscovery()
        super.onPause()
    }

    override fun onDestroy() {
        soundManager.release()
        super.onDestroy()
    }
}
