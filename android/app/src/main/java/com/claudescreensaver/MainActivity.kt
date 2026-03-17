package com.claudescreensaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.claudescreensaver.data.network.SkinListItem
import com.claudescreensaver.ui.screens.AdGateScreen
import com.claudescreensaver.ui.screens.OnboardingScreen
import com.claudescreensaver.ui.screens.PaywallScreen
import com.claudescreensaver.ui.screens.SettingsScreen
import com.claudescreensaver.ui.screens.SessionFullScreen
import com.claudescreensaver.ui.screens.SkinMarketplaceScreen
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
    private var powerReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kiosk mode: show over lock screen, keep screen on, dismiss keyguard
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        bridgeDiscovery = BridgeDiscovery(this)
        viewModel = StatusViewModel(SseClient())
        viewModel.skinEngine.loadBuiltInSkins(this)
        soundManager = SoundManager(this)
        billingManager = BillingManager(this)
        billingManager.initialize()

        // Autoconnect: if we have a saved URL, connect immediately
        val prefs = getSharedPreferences("claude_screensaver", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", "") ?: ""
        if (savedUrl.isNotBlank()) {
            viewModel.connect(savedUrl)
        }

        // Auto-launch dashboard when charging if kiosk mode enabled
        val kioskEnabled = prefs.getBoolean("kiosk_mode", false)
        val isCharging = isDeviceCharging()

        enableEdgeToEdge()

        setContent {
            ClaudeScreenSaverTheme(skin = viewModel.uiState.collectAsState().value.activeSkin) {
                val uiState by viewModel.uiState.collectAsState()
                val servers by bridgeDiscovery.servers.collectAsState()
                val proStatus by billingManager.proStatus.collectAsState()
                val billingProducts by billingManager.products.collectAsState()
                val scope = rememberCoroutineScope()

                val isAdFree = proStatus == ProStatus.PRO || proStatus == ProStatus.TRIAL

                // Screen navigation
                // Kiosk auto-launch: ad-free users go straight to dashboard,
                // free users see ad gate first
                val initialScreen = when {
                    kioskEnabled && isCharging && savedUrl.isNotBlank() && isAdFree -> "dashboard"
                    kioskEnabled && isCharging && savedUrl.isNotBlank() -> "ad_gate"
                    savedUrl.isBlank() -> "onboarding"
                    else -> "settings"
                }
                var currentScreen by remember { mutableStateOf(initialScreen) }
                // Focused session for full-screen view (null = grid view)
                var focusedSessionId by remember { mutableStateOf<String?>(null) }
                // Marketplace: remote skin list from bridge
                var remoteSkins by remember { mutableStateOf<List<SkinListItem>>(emptyList()) }

                // Back navigation: go back through screens instead of closing app
                BackHandler(enabled = currentScreen != "settings" && currentScreen != "onboarding") {
                    when (currentScreen) {
                        "dashboard" -> {
                            if (focusedSessionId != null) {
                                focusedSessionId = null
                            } else {
                                currentScreen = "settings"
                            }
                        }
                        "skins", "paywall", "ad_gate", "demo" -> currentScreen = "settings"
                        else -> currentScreen = "settings"
                    }
                }

                // Display mode — reactive to settings changes
                var displayMode by remember {
                    mutableStateOf(prefs.getString("display_mode", "advanced") ?: "advanced")
                }
                DisposableEffect(Unit) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == "display_mode") {
                            displayMode = prefs.getString("display_mode", "advanced") ?: "advanced"
                        }
                    }
                    prefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                // Sounds always enabled (cosmetic-only paywall)
                LaunchedEffect(Unit) {
                    soundManager.setEnabled(true)
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                ) {
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
                                displayMode = displayMode,
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
                        val focusedStatus = focusedSessionId?.let { uiState.sessions[it] }
                        if (focusedStatus != null) {
                            SessionFullScreen(
                                status = focusedStatus,
                                onBack = { focusedSessionId = null },
                                onSendInput = { text ->
                                    viewModel.sendInput(focusedStatus.sessionId, text)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                StatusDashboardScreen(
                                    uiState = uiState,
                                    displayMode = displayMode,
                                    onSessionTap = { sessionId -> focusedSessionId = sessionId },
                                    modifier = Modifier.fillMaxSize(),
                                )

                                // Settings gear — tap to go back to connection screen
                                SmallFloatingActionButton(
                                    onClick = { currentScreen = "settings" },
                                    containerColor = ClaudeAccentDeep.copy(alpha = 0.6f),
                                    contentColor = ClaudeTextLight,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = "\u2699",
                                        fontSize = 16.sp,
                                    )
                                }
                            }
                        }
                    }
                    "ad_gate" -> {
                        // Placeholder ad gate — free users see this before screensaver
                        // TODO: Replace with actual AdMob interstitial
                        AdGateScreen(
                            onAdComplete = { currentScreen = "dashboard" },
                            onUpgrade = { currentScreen = "paywall" },
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
                                currentScreen = "dashboard"
                            },
                        )
                    }
                    "skins" -> {
                        SkinMarketplaceScreen(
                            skinEngine = viewModel.skinEngine,
                            remoteSkins = remoteSkins,
                            activeSkinId = uiState.activeSkin.id,
                            onFetchSkin = { skinId, callback ->
                                viewModel.sseClient.fetchSkinJson(skinId, callback)
                            },
                            onUploadSkin = { json, callback ->
                                viewModel.sseClient.uploadSkin(json, callback)
                            },
                            onBack = { currentScreen = "settings" },
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = {
                                        // Fetch remote skins when navigating to marketplace
                                        viewModel.sseClient.fetchSkinList { remoteSkins = it }
                                        currentScreen = "skins"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ClaudeAccentDeep),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Skins")
                                }
                                Button(
                                    onClick = {
                                        if (isAdFree) {
                                            currentScreen = "dashboard"
                                        } else {
                                            // Free users see ad before entering screensaver
                                            currentScreen = "ad_gate"
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ClaudeAccent),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Preview ScreenSaver")
                                }
                            }
                        }
                    }
                }
                } // Box with systemBarsPadding

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
        registerPowerReceiver()
    }

    override fun onPause() {
        bridgeDiscovery.stopDiscovery()
        unregisterPowerReceiver()
        super.onPause()
    }

    override fun onDestroy() {
        soundManager.release()
        billingManager.destroy()
        super.onDestroy()
    }

    private fun isDeviceCharging(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
               status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun registerPowerReceiver() {
        if (powerReceiver != null) return
        val prefs = getSharedPreferences("claude_screensaver", Context.MODE_PRIVATE)
        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_POWER_CONNECTED) {
                    val kioskEnabled = prefs.getBoolean("kiosk_mode", false)
                    if (kioskEnabled) {
                        // Bring activity to front when charging starts
                        val launchIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(launchIntent)
                    }
                }
            }
        }
        registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_POWER_CONNECTED))
    }

    private fun unregisterPowerReceiver() {
        powerReceiver?.let {
            unregisterReceiver(it)
            powerReceiver = null
        }
    }
}
