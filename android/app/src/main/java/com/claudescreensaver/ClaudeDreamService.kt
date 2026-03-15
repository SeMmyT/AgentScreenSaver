package com.claudescreensaver

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.claudescreensaver.data.network.SseClient
import com.claudescreensaver.ui.screens.StatusDashboardScreen
import com.claudescreensaver.ui.theme.ClaudeScreenSaverTheme
import com.claudescreensaver.viewmodel.StatusViewModel

class ClaudeDreamService : DreamServiceCompat() {

    private lateinit var viewModel: StatusViewModel

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        viewModel = StatusViewModel(SseClient())

        setContent {
            ClaudeScreenSaverTheme {
                val uiState by viewModel.uiState.collectAsState()
                StatusDashboardScreen(uiState = uiState)
            }
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        // Read server URL from SharedPreferences (not hardcoded)
        val prefs = getSharedPreferences("claude_screensaver", Context.MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "") ?: ""
        if (serverUrl.isNotEmpty()) {
            viewModel.connect(serverUrl)
        }
    }

    override fun onDreamingStopped() {
        viewModel.disconnect()
        super.onDreamingStopped()
    }
}
