package com.claudescreensaver

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.claudescreensaver.ui.screens.StatusDashboardScreen
import com.claudescreensaver.ui.theme.ClaudeScreenSaverTheme
import com.claudescreensaver.viewmodel.StatusViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ClaudeScreenSaverTheme {
                val viewModel: StatusViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                StatusDashboardScreen(uiState = uiState)
            }
        }
    }
}
