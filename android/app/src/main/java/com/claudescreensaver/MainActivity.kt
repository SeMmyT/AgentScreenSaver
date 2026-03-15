package com.claudescreensaver

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudescreensaver.data.BillingManager
import com.claudescreensaver.data.DemoDataProvider
import com.claudescreensaver.data.ProStatus
import com.claudescreensaver.data.SoundManager
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.AgentStatus
import com.claudescreensaver.data.network.BridgeDiscovery
import com.claudescreensaver.data.network.ConnectionState
import com.claudescreensaver.data.network.SseClient
import com.claudescreensaver.ui.screens.OnboardingScreen
import com.claudescreensaver.ui.screens.PaywallScreen
import com.claudescreensaver.ui.screens.SettingsScreen
import com.claudescreensaver.ui.screens.StatusDashboardScreen
import com.claudescreensaver.ui.theme.ClaudeAccent
import com.claudescreensaver.ui.theme.ClaudeAccentDeep
import com.claudescreensaver.ui.theme.ClaudeScreenSaverTheme
import com.claudescreensaver.ui.theme.ClaudeTextLight
import com.claudescreensaver.viewmodel.StatusViewModel
import com.claudescreensaver.viewmodel.UiState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bridgeDiscovery: BridgeDiscovery
    private lateinit var viewModel: StatusViewModel
    private lateinit var soundManager: SoundManager
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        bridgeDiscovery = BridgeDiscovery(this)
        viewModel = StatusViewModel(SseClient())
        soundManager = SoundManager(this)
        billingManager = BillingManager(this)
        billingManager.initialize()

        // Autoconnect: if we have a saved URL, connect immediately
        val prefs = getSharedPreferences("claude_screensaver", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "") ?: ""
        if (savedUrl.isNotBlank()) {
            viewModel.connect(savedUrl)
        }

        setContent {
            ClaudeScreenSaverTheme {
                val uiState by viewModel.uiState.collectAsState()
                val servers by bridgeDiscovery.servers.collectAsState()
                val proStatus by billingManager.proStatus.collectAsState()
                val billingProducts by billingManager.products.collectAsState()
                val scope = rememberCoroutineScope()

                // Screen navigation: show onboarding on first launch (no saved URL)
                val initialScreen = if (savedUrl.isBlank()) "onboarding" else "settings"
                var currentScreen by remember { mutableStateOf(initialScreen) }

                val isPro = proStatus == ProStatus.PRO || proStatus == ProStatus.TRIAL

                // Gate sounds: disabled when FREE
                LaunchedEffect(isPro) {
                    soundManager.setEnabled(isPro)
                }

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

                when (currentScreen) {
                    "onboarding" -> {
                        OnboardingScreen(
                            onGetStarted = { currentScreen = "settings" },
                            onTryDemo = { currentScreen = "demo" },
                        )
                    }
                    "demo" -> {
                        // Collect demo data as state
                        val demoSessions by DemoDataProvider.demoFlow()
                            .collectAsState(initial = emptyMap())

                        // Build a synthetic UiState for the dashboard
                        val primarySession = demoSessions.values.firstOrNull()
                            ?: AgentStatus.DISCONNECTED
                        val demoUiState = UiState(
                            agentStatus = primarySession,
                            sessions = demoSessions,
                            connectionState = ConnectionState.CONNECTED,
                        )

                        Box(modifier = Modifier.fillMaxSize()) {
                            StatusDashboardScreen(
                                uiState = demoUiState,
                                isPro = true,
                                modifier = Modifier.fillMaxSize(),
                            )

                            // EXIT DEMO floating button
                            SmallFloatingActionButton(
                                onClick = { currentScreen = "onboarding" },
                                containerColor = ClaudeAccentDeep,
                                contentColor = ClaudeTextLight,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = "EXIT DEMO",
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            }
                        }
                    }
                    "dashboard" -> {
                        StatusDashboardScreen(
                            uiState = uiState,
                            isPro = isPro,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    "paywall" -> {
                        PaywallScreen(
                            proStatus = proStatus,
                            trialDaysRemaining = billingManager.trialDaysRemaining(),
                            products = billingProducts,
                            onPurchase = { product ->
                                billingManager.launchPurchase(this@MainActivity, product)
                            },
                            onContinueFree = {
                                // Go to dashboard with limited features
                                currentScreen = "dashboard"
                            },
                        )
                    }
                    else -> {
                        // Settings screen
                        Column {
                            SettingsScreen(
                                uiState = uiState,
                                discoveredServers = servers,
                                onConnect = { url -> viewModel.connect(url) },
                                onDisconnect = { viewModel.disconnect() },
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    if (isPro) {
                                        currentScreen = "dashboard"
                                    } else {
                                        currentScreen = "paywall"
                                    }
                                },
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

                // If user purchases from paywall, auto-navigate to dashboard
                LaunchedEffect(proStatus) {
                    if (proStatus == ProStatus.PRO && currentScreen == "paywall") {
                        currentScreen = "dashboard"
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
        billingManager.destroy()
        super.onDestroy()
    }
}
