package com.claudescreensaver.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claudescreensaver.data.ProStatus
import com.claudescreensaver.data.models.AgentStatus
import com.claudescreensaver.data.models.Skin
import com.claudescreensaver.data.network.ConnectionState
import com.claudescreensaver.data.network.SseClient
import com.claudescreensaver.data.skins.SkinEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class UiState(
    val agentStatus: AgentStatus = AgentStatus.DISCONNECTED,
    val sessions: Map<String, AgentStatus> = emptyMap(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val proStatus: ProStatus = ProStatus.FREE,
    val activeSkin: Skin = Skin.DEFAULT,
)

class StatusViewModel(
    val sseClient: SseClient = SseClient(),
    val skinEngine: SkinEngine = SkinEngine(),
) : ViewModel() {

    val uiState: StateFlow<UiState> = combine(
        sseClient.status,
        sseClient.sessions,
        sseClient.connectionState,
        skinEngine.activeSkin,
    ) { status, sessions, conn, skin ->
        UiState(agentStatus = status, sessions = sessions, connectionState = conn, activeSkin = skin)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState(),
    )

    fun connect(url: String) {
        sseClient.connect(url)
    }

    fun disconnect() {
        sseClient.disconnect()
    }

    fun sendInput(sessionId: String, text: String) {
        sseClient.sendInput(sessionId, text)
    }

    override fun onCleared() {
        super.onCleared()
        sseClient.disconnect()
    }
}
