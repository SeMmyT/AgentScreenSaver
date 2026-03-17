package com.claudescreensaver

import android.content.Context
import android.graphics.Color
import android.view.WindowManager
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
        isScreenBright = true  // Keep screen readable on charging stand

        // Force opaque dark background on the dream window
        window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#141413")))
            w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER.inv() and 0) // no-op but ensures no wallpaper
        }

        viewModel = StatusViewModel(SseClient())

        val prefs = getSharedPreferences("claude_screensaver", Context.MODE_PRIVATE)

        setContent {
            ClaudeScreenSaverTheme(skin = viewModel.uiState.collectAsState().value.activeSkin) {
                val uiState by viewModel.uiState.collectAsState()
                val displayMode = prefs.getString("display_mode", "advanced") ?: "advanced"
                StatusDashboardScreen(
                    uiState = uiState,
                    displayMode = displayMode,
                )
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
